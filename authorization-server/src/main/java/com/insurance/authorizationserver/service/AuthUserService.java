package com.insurance.authorizationserver.service;

import com.insurance.authorizationserver.dto.CreateUserRequest;
import com.insurance.authorizationserver.model.AuthRole;
import com.insurance.authorizationserver.model.AuthUser;
import com.insurance.authorizationserver.repository.AuthRoleRepository;
import com.insurance.authorizationserver.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthUserService {

    private final AuthUserRepository authUserRepository;
    private final AuthRoleRepository authRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.user-service-uri:http://localhost:8081}")
    private String userServiceUri;

    @Transactional
    public void createUser(CreateUserRequest request) {
        if (authUserRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (authUserRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            if (authUserRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
                throw new IllegalArgumentException("Phone number already exists");
            }
        }

        String roleStr = request.getRole() == null ? "USER" : request.getRole().toUpperCase();
        String formattedRole = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;

        AuthRole authRole = authRoleRepository.findByRoleName(formattedRole)
                .orElseGet(() -> authRoleRepository.save(AuthRole.builder().roleName(formattedRole).build()));

        AuthUser authUser = AuthUser.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(authRole))
                .build();

        authUser = authUserRepository.save(authUser);

        // Sync with User Service
        syncUserProfile(authUser, request);
    }

    private void syncUserProfile(AuthUser authUser, CreateUserRequest request) {
        try {
            Map<String, Object> profileBody = new HashMap<>();
            profileBody.put("authUserId", authUser.getAuthUserId().toString());
            profileBody.put("email", authUser.getEmail());
            profileBody.put("phoneNumber", authUser.getPhoneNumber());
            profileBody.put("fullName", request.getFullName());
            profileBody.put("identityNumber", request.getIdentityNumber());
            profileBody.put("dateOfBirth", request.getDateOfBirth() != null ? request.getDateOfBirth().toString() : null);
            profileBody.put("gender", request.getGender());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-USER-ROLE", "SYSTEM");
            headers.set("X-USER-ID", UUID.randomUUID().toString());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(profileBody, headers);

            log.info("Sending profile sync request to User Service: {}", userServiceUri + "/internal/users");
            restTemplate.postForEntity(userServiceUri + "/internal/users", entity, Object.class);
        } catch (Exception e) {
            log.error("Failed to sync profile to User Service", e);
            throw new IllegalStateException("Profile sync failed: " + e.getMessage(), e);
        }
    }
}
