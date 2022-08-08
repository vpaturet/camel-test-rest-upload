package org.test.rest;

import com.nimbusds.jose.JWSAlgorithm;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.test.TestApp;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;

@CamelSpringBootTest
@UseAdviceWith
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class RestRouteTest {

    @TestConfiguration
    @EnableWebSecurity
    static class RestRouteTestContextConfiguration extends WebSecurityConfigurerAdapter {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedHeaders(Arrays.asList("Origin", "Accept", "X-Requested-With", "Content-Type", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Authorization", "x-correlation-id"));
            configuration.addAllowedOrigin("*");
            configuration.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "DELETE"));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http.cors(withDefaults())
                    .csrf().disable()
                    .authorizeRequests()
                    .anyRequest().authenticated()
                    .and()
                    .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
                    // comment out this line to disable OAuth2 client
                    .oauth2Client();
        }

        @Bean
        public JwtDecoder jwtdecoder() {
            return new JwtDecoder() {
                @Override
                public Jwt decode(String token) throws JwtException {
                    return createTestJwtToken();
                }
            };
        }

        private Jwt createTestJwtToken() {
            String userId = "test-user";
            String userName = "Test User";

            return Jwt.withTokenValue("test-token")
                    .header("typ", "JWT")
                    .header("alg", JWSAlgorithm.RS256.getName())
                    .claim("iss", "https://test-issuer.entur.org")
                    .claim("scope", "openid profile email")
                    .subject(userId)
                    .audience(Set.of("test-audience"))
                    .build();
        }
    }

    @Autowired
    ModelCamelContext camelContext;

    @Produce("http:localhost:{{server.port}}/services/upload/files")
    protected ProducerTemplate postFileTemplate;

    @EndpointInject("mock:processUpload")
    protected MockEndpoint processUpload;

    @Test
    void postFile() throws Exception {

        String fileName = "test-POST.zip";

        AdviceWith.adviceWith(camelContext, "upload", a -> {
                    a.interceptSendToEndpoint("direct:processUpload")
                            .skipSendToOriginalEndpoint()
                            .to("mock:processUpload");
                }
        );

        processUpload.expectedMessageCount(1);

        camelContext.start();

        HttpEntity httpEntity = MultipartEntityBuilder.create().addBinaryBody(fileName, getLargeTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName).build();
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        postFileTemplate.requestBodyAndHeaders(httpEntity, headers);
        processUpload.assertIsSatisfied();
        byte[] fileContent = processUpload.getExchanges().stream().findFirst().orElseThrow().getIn().getBody(byte[].class);
        Assertions.assertTrue(fileContent.length > 0);


    }

    private InputStream getLargeTestNetexArchiveAsStream() {
        return getClass().getResourceAsStream("/large.zip");
    }

}