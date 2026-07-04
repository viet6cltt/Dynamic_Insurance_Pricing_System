package com.insurance.notificationservice.client;

import com.insurance.notificationservice.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "notification-user-service-client", url = "${app.services.user-service-url}")
public interface UserServiceClient {

    @GetMapping("/internal/users/by-auth-user/{authUserId}")
    UserProfileResponse getUserProfileByAuthUserId(@PathVariable UUID authUserId,
                                                   @RequestHeader("X-USER-ID") String systemUserId,
                                                   @RequestHeader("X-USER-ROLE") String systemRole);
}
