package com.idempotent.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.catalina.connector.Connector;

/**
 * Configuration to redirect HTTP requests to HTTPS.
 * This enables port 8080 (HTTP) to redirect to port 443 (HTTPS).
 */
@Configuration
public class HttpsRedirectConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            // Create HTTP connector on port 8080 that redirects to HTTPS
            Connector redirectConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            redirectConnector.setPort(8080);
            redirectConnector.setSecure(false);
            redirectConnector.setScheme("http");
            redirectConnector.setAttribute("redirectPort", 443);
            
            factory.addAdditionalTomcatConnectors(redirectConnector);
        };
    }
}
