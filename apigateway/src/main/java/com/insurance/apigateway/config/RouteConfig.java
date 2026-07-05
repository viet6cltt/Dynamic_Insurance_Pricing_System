package com.insurance.apigateway.config;

import com.insurance.apigateway.filter.AuthenticationHeaderFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@Configuration
public class RouteConfig {

    @Value("${app.services.user-service-url:http://localhost:8081}")
    private String userServiceUrl;

    @Value("${app.services.auth-service-url:http://localhost:9000}")
    private String authServiceUrl;

    @Value("${app.services.product-service-url:http://localhost:8082}")
    private String productServiceUrl;

    @Value("${app.services.pricing-service-url:http://localhost:8083}")
    private String pricingServiceUrl;

    @Value("${app.services.policy-service-url:http://localhost:8084}")
    private String policyServiceUrl;

    @Value("${app.services.payment-service-url:http://localhost:8085}")
    private String paymentServiceUrl;

    @Value("${app.services.notification-service-url:http://localhost:8086}")
    private String notificationServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> insuranceRoutes(AuthenticationHeaderFilter authenticationHeaderFilter) {
        return route("pricing-service-route")
                .route(path("/pricing/**").or(path("/pricing")), http())
                .before(authenticationHeaderFilter.addAuthenticationHeader())
                .before(uri(pricingServiceUrl))
                .build()
                .and(route("application-policy-service-route")
                        .route(path("/contracts/**").or(path("/contracts")), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(policyServiceUrl))
                        .build())
                .and(route("payment-service-route")
                        .route(path("/payments/**").or(path("/payments")), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(paymentServiceUrl))
                        .build())
                .and(route("notification-service-route")
                        .route(path("/notifications/**").or(path("/notifications")), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(notificationServiceUrl))
                        .build())
                .and(route("user-service-route")
                        .route(path("/users/**").or(path("/users")), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(userServiceUrl))
                        .build())
                .and(route("insured-person-service-route")
                        .route(path("/insured-persons/**").or(path("/insured-persons")), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(userServiceUrl))
                        .build())
                .and(route("product-service-route")
                        .route(path("/products/**").or(path("/products")), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(productServiceUrl))
                        .build())
                .and(route("product-admin-service-route")
                        .route(path("/admin/**").or(path("/admin")), http())
                        .before(authenticationHeaderFilter.addAuthenticationHeader())
                        .before(uri(productServiceUrl))
                        .build())
                .and(route("user-service-docs-route")
                        .route(path("/docs/user/v3/api-docs"), http())
                        .before(setPath("/v3/api-docs"))
                        .before(uri(userServiceUrl))
                        .build())
                .and(route("auth-service-docs-route")
                        .route(path("/docs/auth/v3/api-docs"), http())
                        .before(setPath("/v3/api-docs"))
                        .before(uri(authServiceUrl))
                        .build())
                .and(route("product-service-docs-route")
                        .route(path("/docs/product/v3/api-docs"), http())
                        .before(setPath("/v3/api-docs"))
                        .before(uri(productServiceUrl))
                        .build())
                .and(route("pricing-service-docs-route")
                        .route(path("/docs/pricing/v3/api-docs"), http())
                        .before(setPath("/v3/api-docs"))
                        .before(uri(pricingServiceUrl))
                        .build())
                .and(route("application-policy-service-docs-route")
                        .route(path("/docs/policy/v3/api-docs"), http())
                        .before(setPath("/v3/api-docs"))
                        .before(uri(policyServiceUrl))
                        .build())
                .and(route("payment-service-docs-route")
                        .route(path("/docs/payment/v3/api-docs"), http())
                        .before(setPath("/v3/api-docs"))
                        .before(uri(paymentServiceUrl))
                        .build())
                .and(route("notification-service-docs-route")
                        .route(path("/docs/notification/v3/api-docs"), http())
                        .before(setPath("/v3/api-docs"))
                        .before(uri(notificationServiceUrl))
                        .build());
    }
}
