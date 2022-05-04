/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package org.test.rest;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

@Component
public class RestRouteBuilder extends RouteBuilder {


    private static final String PLAIN = "text/plain";

    @Override
    public void configure() throws Exception {

        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());


        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());


        restConfiguration()
                .component("servlet")
                .contextPath("/services");


        rest("/upload")
                .post("/files")
                .description("Upload file")
                .consumes(MULTIPART_FORM_DATA)
                .produces(PLAIN)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .route()
                .convertBodyTo(byte[].class)
                .to("direct:processUpload")
                .routeId("rest-upload")
                .endRest();

        from("direct:processUpload")
                .log(LoggingLevel.INFO, "Uploaded files")
                .routeId("process-upload");
    }
}
