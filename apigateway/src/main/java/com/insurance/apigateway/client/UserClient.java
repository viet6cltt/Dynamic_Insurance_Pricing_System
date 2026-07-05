package com.insurance.apigateway.client;

import com.insurance.apigateway.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "USERSERVICE", path = "/internal/users", configuration = FeignConfig.class)
public interface UserClient {

    @GetMapping("/by-auth-user/{userId}")
    UserProfileResponse findUserProfile(@PathVariable String userId);
}
