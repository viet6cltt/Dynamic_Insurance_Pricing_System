package com.insurance.apigateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limit policies configuration.
 * Each policy defines: maxRequests per window duration.
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private PolicyProps userProfileUpdate = new PolicyProps(true, 5, Duration.ofMinutes(1));
    private PolicyProps quoteCreation = new PolicyProps(true, 10, Duration.ofMinutes(1));
    private PolicyProps generalApi = new PolicyProps(true, 200, Duration.ofMinutes(1));

    public PolicyProps getUserProfileUpdate() { return userProfileUpdate; }
    public void setUserProfileUpdate(PolicyProps p) { this.userProfileUpdate = p; }

    public PolicyProps getQuoteCreation() { return quoteCreation; }
    public void setQuoteCreation(PolicyProps p) { this.quoteCreation = p; }

    public PolicyProps getGeneralApi() { return generalApi; }
    public void setGeneralApi(PolicyProps p) { this.generalApi = p; }

    public static class PolicyProps {
        private boolean enabled;
        private int maxRequests;
        private Duration window;

        public PolicyProps() {}

        public PolicyProps(boolean enabled, int maxRequests, Duration window) {
            this.enabled = enabled;
            this.maxRequests = maxRequests;
            this.window = window;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxRequests() { return Math.max(1, maxRequests); }
        public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }

        public Duration getWindow() {
            return (window == null || window.isZero() || window.isNegative())
                    ? Duration.ofMinutes(1) : window;
        }
        public void setWindow(Duration window) { this.window = window; }
    }
}
