package com.insurance.authorizationserver.controller;

import com.insurance.authorizationserver.dto.CreateUserRequest;
import com.insurance.authorizationserver.dto.LoginRequest;
import com.insurance.authorizationserver.dto.LoginResponse;
import com.insurance.authorizationserver.dto.RefreshTokenRequest;
import com.insurance.authorizationserver.repository.AuthUserRepository;
import com.insurance.authorizationserver.security.DatabaseUserDetailsService;
import com.insurance.authorizationserver.security.SecurityUser;
import com.insurance.authorizationserver.service.AuthUserService;
import com.insurance.authorizationserver.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersController {

    private final AuthUserService authUserService;
    private final DatabaseUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthUserRepository authUserRepository;

    @PostMapping("/register")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        authUserService.createUser(request);
        return new ResponseEntity<>("User created and profile synchronized!", HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        SecurityUser user;
        try {
            user = (SecurityUser) userDetailsService.loadUserByUsername(request.getUsername());
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password");
        }

        return ResponseEntity.ok(buildLoginResponse(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Refresh token is required");
            }

            var userId = tokenService.validateRefreshToken(request.getRefreshToken());
            SecurityUser user = authUserRepository.findById(userId)
                    .map(SecurityUser::new)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            return ResponseEntity.ok(buildLoginResponse(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid refresh token");
        }
    }

    private LoginResponse buildLoginResponse(SecurityUser user) {
        String role = user.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER");

        return LoginResponse.builder()
                .accessToken(tokenService.issue(user))
                .refreshToken(tokenService.issueRefreshToken(user))
                .tokenType("Bearer")
                .expiresIn(tokenService.getTokenValiditySeconds())
                .refreshExpiresIn(tokenService.getRefreshTokenValiditySeconds())
                .role(role)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
