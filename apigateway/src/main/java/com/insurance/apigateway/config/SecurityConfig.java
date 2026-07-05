package com.insurance.apigateway.config;

import com.insurance.apigateway.ratelimit.ProfileUpdateRateLimitFilter;
import com.insurance.apigateway.security.CustomAuthenticationManagerResolver;
import com.insurance.apigateway.security.CustomJwtAuthenticationConverter;
import com.insurance.apigateway.security.GoogleJwtAuthenticationConverter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Value("${spring.security.oauth2.google.issuer}")
    private String googleIssuer;
    @Value("${spring.security.oauth2.google.jwk-uri}")
    private String googleJwkUri;
    @Value("${spring.security.oauth2.insurance.issuer}")
    private String insuranceIssuer;
    @Value("${spring.security.oauth2.insurance.jwk-uri}")
    private String insuranceJwkUri;
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ProfileUpdateRateLimitFilter profileUpdateRateLimitFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.oauth2ResourceServer(j -> j.authenticationManagerResolver(
                new CustomAuthenticationManagerResolver(
                        new CustomJwtAuthenticationConverter(),
                        new GoogleJwtAuthenticationConverter(),
                        googleIssuer, googleJwkUri,
                        insuranceIssuer, insuranceJwkUri)));
        http.cors(c -> {
            CorsConfigurationSource source = request -> {
                String origin = request.getHeader("Origin");
                CorsConfiguration corsConfiguration = new CorsConfiguration();
                if (origin == null || origin.isBlank()) {
                    // Mobile / native client — no Origin header, allow all without credentials
                    corsConfiguration.addAllowedOriginPattern("*");
                    corsConfiguration.setAllowCredentials(false);
                } else {
                    String[] origins = allowedOrigins.split(",");
                    for (String o : origins) {
                        corsConfiguration.addAllowedOriginPattern(o.trim());
                    }
                    corsConfiguration.setAllowCredentials(true);
                }
                corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                corsConfiguration.setAllowedHeaders(List.of("*"));
                corsConfiguration.setMaxAge(3600L);
                return corsConfiguration;
            };
            c.configurationSource(source);
        });
        http.authorizeHttpRequests(
                c -> c.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/docs/**").permitAll()
                        .anyRequest().authenticated());
        http.addFilterAfter(profileUpdateRateLimitFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<ProfileUpdateRateLimitFilter> profileUpdateRateLimitFilterRegistration(
            ProfileUpdateRateLimitFilter profileUpdateRateLimitFilter) {
        FilterRegistrationBean<ProfileUpdateRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(profileUpdateRateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

}
