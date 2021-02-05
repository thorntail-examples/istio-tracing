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

import io.thorntail.openshift.test.AdditionalResources;
import io.thorntail.openshift.test.OpenShiftTest;
import io.thorntail.openshift.test.injection.TestResource;
import io.thorntail.openshift.test.injection.WithName;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

@OpenShiftTest
@AdditionalResources("classpath:istio-gateway.yaml")
public class OpenshiftIT {
    private static final String ISTIO_NAMESPACE = "istio-system";
    private static final String JAEGER_NAME = "jaeger";
    private static final String ISTIO_INGRESS_GATEWAY_NAME = "istio-ingressgateway";

    @TestResource
    @WithName(value = JAEGER_NAME, inNamespace = ISTIO_NAMESPACE)
    private URL jaeger;

    @TestResource
    @WithName(value = ISTIO_INGRESS_GATEWAY_NAME, inNamespace = ISTIO_NAMESPACE)
    private URL ingressGateway;

    @BeforeEach
    public void setUp() {
        await().ignoreExceptions().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
            given()
                    .baseUri(ingressGateway + "/thorntail-istio-tracing")
            .when()
                    .get()
            .then()
                    .statusCode(200);
        });
    }

    @Test
    public void tracingTest() {
        long startTime = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
                - TimeUnit.SECONDS.toMicros(1); // tolerate 1 sec of skew between localhost and Minishift VM

        given()
                .baseUri(ingressGateway + "/thorntail-istio-tracing")
        .when()
                .get("/api/greeting")
        .then()
                .statusCode(200)
                .body("content", startsWith("Hello"));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, Map> processes =
                    given()
                            .baseUri(jaeger.toString())
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
