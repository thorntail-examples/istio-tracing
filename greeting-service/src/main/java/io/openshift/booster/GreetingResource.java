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

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

/**
 * @author Ken Finnigan
 */
@Path("/")
public class GreetingResource {

    private static final String NAME_SERVICE_URL = "http://wfswarm-istio-tracing-cute-name:8080";

    @GET
    @Path("/greeting")
    @Produces("application/json")
    public Response greeting(@HeaderParam("x-request-id") String xreq,
                             @HeaderParam("x-b3-traceid") String xtraceid,
                             @HeaderParam("x-b3-spanid") String xspanid,
                             @HeaderParam("x-b3-parentspanid") String xparentspanid,
                             @HeaderParam("x-b3-sampled") String xsampled,
                             @HeaderParam("x-b3-flags") String xflags,
                             @HeaderParam("x-ot-span-context") String xotspan) {
        try {
            Client client = ClientBuilder.newClient();
            WebTarget webTarget = client.target(NAME_SERVICE_URL);
            Invocation.Builder requestBuilder = webTarget.path("/api/name").request();

            // Setup Tracing Headers
            if (null != xreq) {
                requestBuilder.header("x-request-id", xreq);
            }
            if (null != xtraceid) {
                requestBuilder.header("x-b3-traceid", xtraceid);
            }
            if (null != xspanid) {
                requestBuilder.header("x-b3-spanid", xspanid);
            }
            if (null != xparentspanid) {
                requestBuilder.header("x-b3-parentspanid", xparentspanid);
            }
            if (null != xsampled) {
                requestBuilder.header("x-b3-sampled", xsampled);
            }
            if (null != xflags) {
                requestBuilder.header("x-b3-flags", xflags);
            }
            if (null != xotspan) {
                requestBuilder.header("x-ot-span-context", xotspan);
            }

            String name = requestBuilder.get().readEntity(String.class);

            return Response.ok()
                    .entity(new Greeting(String.format("Hello %s", name)))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity("Failed to communicate with `wfswarm-istio-tracing-name` due to: " + e.getMessage())
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
