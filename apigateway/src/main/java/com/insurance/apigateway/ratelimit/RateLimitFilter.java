package com.insurance.apigateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Unified rate limiting filter covering three policies:
 *
 * <ol>
 *   <li><b>quote-creation</b> – POST /pricing/quotes (AI-backed, expensive)</li>
 *   <li><b>user-profile-update</b> – PUT /api/v1/users/users/{id}</li>
 *   <li><b>general-api</b> – all other authenticated requests (global per-user guard)</li>
 * </ol>
 *
 * Policies are checked in priority order; the first match that triggers a block wins.
 * All counters are stored in Redis with a sliding-window counter strategy.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final Pattern QUOTE_CREATION_PATH = Pattern.compile("^/pricing/quotes/?$");
    private static final Pattern PROFILE_UPDATE_PATH = Pattern.compile("^/api/v1/users/users/[^/]+/?$");

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticatedUser(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = authentication.getName();
        String path = normalizedPath(request);
        String method = request.getMethod();

        // --- Policy 1: Quote creation (POST /pricing/quotes) ---
        if (HttpMethod.POST.matches(method) && QUOTE_CREATION_PATH.matcher(path).matches()) {
            RateLimitProperties.PolicyProps policy = properties.getQuoteCreation();
            if (policy.isEnabled() && isBlocked(userId, "quote_creation", policy, response)) {
                writeRateLimitError(response, "Too many quote requests. Please wait before creating another quote.");
                return;
            }
        }

        // --- Policy 2: Profile update (PUT /api/v1/users/users/{id}) ---
        if (HttpMethod.PUT.matches(method) && PROFILE_UPDATE_PATH.matcher(path).matches()) {
            RateLimitProperties.PolicyProps policy = properties.getUserProfileUpdate();
            if (policy.isEnabled() && isBlocked(userId, "user_profile_update", policy, response)) {
                writeRateLimitError(response, "Too many profile updates. Try again later.");
                return;
            }
        }

        // --- Policy 3: General per-user API limit ---
        RateLimitProperties.PolicyProps generalPolicy = properties.getGeneralApi();
        if (generalPolicy.isEnabled() && isBlocked(userId, "general_api", generalPolicy, response)) {
            writeRateLimitError(response, "Too many requests. Please slow down.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // -----------------------------------------------------------------------

    /**
     * Increments the Redis counter for the given policy+userId and returns
     * true if the request should be blocked (limit exceeded).
     * Always sets rate-limit response headers.
     */
    private boolean isBlocked(String userId,
                               String policyName,
                               RateLimitProperties.PolicyProps policy,
                               HttpServletResponse response) {
        int limit = policy.getMaxRequests();
        Duration window = policy.getWindow();
        String key = "rate-limit:" + policyName + ":" + userId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("Redis returned null for key {}; failing open.", key);
                return false;
            }
            if (count == 1L) {
                redisTemplate.expire(key, window);
            }

            long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl <= 0) ttl = window.toSeconds();

            long remaining = Math.max(0, limit - count);
            boolean blocked = count > limit;

            if (blocked) {
                log.info("Rate limit [{}] BLOCKED userId={} count={} limit={}", policyName, userId, count, limit);
            }

            addHeaders(response, policyName, limit, remaining, ttl, blocked);
            return blocked;

        } catch (RuntimeException ex) {
            log.warn("Rate limit [{}] Redis error — failing open: {}", policyName, ex.getMessage());
            return false;
        }
    }

    private void addHeaders(HttpServletResponse response, String policy,
                            int limit, long remaining, long resetSeconds, boolean blocked) {
        response.setHeader("X-RateLimit-Policy", policy);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));
        if (blocked) {
            response.setHeader("Retry-After", String.valueOf(resetSeconds));
        }
    }

    private void writeRateLimitError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"rate_limit_exceeded\",\"message\":\"" + message + "\"}"
        );
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String name = authentication.getName();
        return StringUtils.hasText(name) && !"anonymousUser".equals(name);
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (StringUtils.hasText(ctx) && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return path;
    }
}
