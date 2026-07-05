package com.insurance.apigateway.filter;

import com.insurance.apigateway.client.UserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.function.Function;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class AuthenticationHeaderFilter {

    private static final Logger logger = Logger.getLogger(AuthenticationHeaderFilter.class.getName());
    private final RedisTemplate<String, String> redisTemplate;
    private final UserClient userClient;

    public Function<ServerRequest, ServerRequest> addAuthenticationHeader() {
        return request -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.info("MISSING CREDENTIAL");
                return request;
            }

            String userId = authentication.getName();
            try {
                java.util.UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                userId = java.util.UUID.nameUUIDFromBytes(userId.getBytes()).toString();
            }
            String role = resolveRole(authentication);
            String email = resolveJwtClaim(authentication, "email");
            String name = resolveJwtClaim(authentication, "name");

            String userStatus = redisTemplate.opsForValue().get(userId + "_status");
            if (userStatus == null) {
                logger.info("CACHE MISS, RESORTING TO USER SERVICE THROUGH HTTP");
                try {
                    userStatus = userClient.findUserProfile(userId).customerStatus();
                } catch (feign.FeignException.NotFound e) {
                    logger.info("USER NOT FOUND");
                    logger.info("VALID REQUEST, PASSING REQUEST TO DOWNSTREAM SERVICE");
                    return withAuthenticationHeaders(request, userId, role, email, name);
                } catch (feign.FeignException e) {
                    logger.warning("USER STATUS CHECK UNAVAILABLE: " + e.status() + " " + e.getMessage());
                    logger.info("VALID REQUEST, PASSING REQUEST TO DOWNSTREAM SERVICE");
                    return withAuthenticationHeaders(request, userId, role, email, name);
                } catch (RuntimeException e) {
                    logger.warning("USER STATUS CHECK FAILED: " + e.getMessage());
                    logger.info("VALID REQUEST, PASSING REQUEST TO DOWNSTREAM SERVICE");
                    return withAuthenticationHeaders(request, userId, role, email, name);
                }
            }

            if ("BANNED".equals(userStatus)) {
                logger.info("INVALID REQUEST - USER BANNED, ABORT");
                return request;
            }

            logger.info("VALID REQUEST, PASSING REQUEST TO DOWNSTREAM SERVICE");
            return withAuthenticationHeaders(request, userId, role, email, name);
        };
    }

    private ServerRequest withAuthenticationHeaders(ServerRequest request,
                                                    String userId,
                                                    String role,
                                                    String email,
                                                    String name) {
        return ServerRequest.from(request)
                .headers(headers -> {
                    headers.remove("X-USER-ID");
                    headers.remove("X-USER-ROLE");
                    headers.remove("X-USER-EMAIL");
                    headers.remove("X-USER-NAME");
                    headers.add("X-USER-ID", userId);
                    headers.add("X-USER-ROLE", role);
                    if (StringUtils.hasText(email)) {
                        headers.add("X-USER-EMAIL", email);
                    }
                    if (StringUtils.hasText(name)) {
                        headers.add("X-USER-NAME", name);
                    }
                })
                .build();
    }

    private String resolveRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
    }

    private String resolveJwtClaim(Authentication authentication, String claimName) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt token = jwtAuthentication.getToken();
            Object claim = token.getClaims().get(claimName);
            return claim == null ? "" : String.valueOf(claim);
        }
        return "";
    }
}
