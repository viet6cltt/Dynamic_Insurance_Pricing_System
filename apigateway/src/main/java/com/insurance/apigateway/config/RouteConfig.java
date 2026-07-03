package com.insurance.apigateway.config;

import com.insurance.apigateway.filter.AuthenticationHeaderFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@Configuration
public class RouteConfig {

    @Value("${app.services.pricing-service-url:http://localhost:8083}")
    private String pricingServiceUrl;

    @Value("${app.services.policy-service-url:http://localhost:8084}")
    private String policyServiceUrl;

    @Value("${app.services.payment-service-url:http://localhost:8085}")
    private String paymentServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> insuranceRoutes(AuthenticationHeaderFilter authenticationHeaderFilter) {
        return route("pricing-service-route")
                .route(path("/pricing/**"), http())
                .before(authenticationHeaderFilter.addAuthenticationHeader())
                .before(uri(pricingServiceUrl))
                .build()
                .and(route("application-policy-service-route")
                        .route(path("/contracts/**"), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(policyServiceUrl))
                        .build())
                .and(route("payment-service-route")
                        .route(path("/payments/**"), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(paymentServiceUrl))
                        .build());
    }
}
