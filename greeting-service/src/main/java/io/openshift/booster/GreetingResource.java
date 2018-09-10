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
package io.openshift.booster;

import java.net.URL;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import io.opentracing.Tracer;
import io.smallrye.opentracing.SmallRyeClientTracingFeature;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * @author Ken Finnigan
 */
@Path("/")
public class GreetingResource {

    private static final String NAME_SERVICE_URL = "http://thorntail-istio-tracing-cute-name:8080";

    @Inject
    Tracer tracer;

    @GET
    @Path("/greeting")
    @Produces("application/json")
    public Response greeting() {
        try {
            NameService nameService =
                    RestClientBuilder.newBuilder()
                            .baseUrl(new URL(NAME_SERVICE_URL))
                            .register(new SmallRyeClientTracingFeature(tracer))
                            .build(NameService.class);

            String name = nameService.getName();

            return Response.ok()
                    .entity(new Greeting(String.format("Hello %s", name)))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity("Failed to communicate with `thorntail-istio-tracing-cute-name` due to: " + e.getMessage())
                    .build();
        }
    }

    static class Greeting {
        private final String content;

        public Greeting(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

}
