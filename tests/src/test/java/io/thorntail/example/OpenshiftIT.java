/*
 *  Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package io.thorntail.example;

import org.arquillian.cube.istio.api.IstioResource;
import org.arquillian.cube.openshift.impl.enricher.AwaitRoute;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.assertj.core.api.Condition;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

@RunWith(Arquillian.class)
@IstioResource("classpath:istio-gateway.yaml")
public class OpenshiftIT {
    private static final String ISTIO_NAMESPACE = "istio-system";
    private static final String JAEGER_QUERY_NAME = "jaeger-query";
    private static final String ISTIO_INGRESS_GATEWAY_NAME = "istio-ingressgateway";

    @RouteURL(value = JAEGER_QUERY_NAME, namespace = ISTIO_NAMESPACE)
    private String jaegerQuery;

    @RouteURL(value = ISTIO_INGRESS_GATEWAY_NAME, path = "/thorntail-istio-tracing", namespace = ISTIO_NAMESPACE)
    @AwaitRoute
    private String ingressGateway;

    @Test
    public void tracingTest() {
        long startTime = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        given()
                .baseUri(ingressGateway)
        .when()
                .get("/api/greeting")
        .then()
                .statusCode(200)
                .content("content", startsWith("Hello"));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, Map> processes =
                    given()
                            .baseUri(jaegerQuery)
                            .relaxedHTTPSValidation()
                    .when()
                            .param("service", ISTIO_INGRESS_GATEWAY_NAME)
                            .param("start", startTime)
                            .get("/api/traces")
                    .then()
                            .statusCode(200)
                            .body("data", notNullValue())
                            .body("data[0]", notNullValue())
                            .body("data[0].processes", notNullValue())
                            .extract()
                            .jsonPath()
                            .getMap("data[0].processes", String.class, Map.class);

            assertThat(processes.values())
                    .isNotEmpty()
                    .extracting("serviceName", String.class)
                    .filteredOn(s -> s.contains("thorntail"))
                    .haveAtLeastOne(isApplicationService("greeting"))
                    .haveAtLeastOne(isApplicationService("cute-name"));
        });
    }

    private Condition<String> isApplicationService(String name) {
        return new Condition<>(s -> s.contains(name), "a trace named: " + name);
    }
}
