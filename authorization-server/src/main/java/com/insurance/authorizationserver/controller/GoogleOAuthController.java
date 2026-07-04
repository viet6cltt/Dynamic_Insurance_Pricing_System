package com.insurance.authorizationserver.controller;

import com.insurance.authorizationserver.dto.GoogleCodeExchangeRequest;
import com.insurance.authorizationserver.dto.LoginResponse;
import com.insurance.authorizationserver.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oauth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchangeCode(@RequestBody GoogleCodeExchangeRequest request) {
        return ResponseEntity.ok(googleOAuthService.exchangeCode(request));
    }
}
