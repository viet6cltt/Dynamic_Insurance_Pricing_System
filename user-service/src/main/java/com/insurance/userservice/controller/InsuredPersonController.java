package com.insurance.userservice.controller;

import com.insurance.userservice.dto.*;
import com.insurance.userservice.service.InsuredPersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/insured-persons")
@RequiredArgsConstructor
public class InsuredPersonController {

    private final InsuredPersonService insuredPersonService;

    @PostMapping
    public ResponseEntity<InsuredPersonResponse> createInsuredPerson(@RequestBody CreateInsuredPersonRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        InsuredPersonResponse response = insuredPersonService.createInsuredPerson(authUserId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<PagedResponse<InsuredPersonResponse>> getMyInsuredPersons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        PagedResponse<InsuredPersonResponse> response = insuredPersonService.getMyInsuredPersons(authUserId, status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{insuredPersonId}")
    public ResponseEntity<InsuredPersonResponse> getInsuredPersonDetail(@PathVariable UUID insuredPersonId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        String role = getUserRole(authentication);
        InsuredPersonResponse response = insuredPersonService.getInsuredPersonDetail(authUserId, role, insuredPersonId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{insuredPersonId}")
    public ResponseEntity<InsuredPersonResponse> updateInsuredPerson(
            @PathVariable UUID insuredPersonId,
            @RequestBody UpdateInsuredPersonRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        String role = getUserRole(authentication);
        InsuredPersonResponse response = insuredPersonService.updateInsuredPerson(authUserId, role, insuredPersonId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{insuredPersonId}")
    public ResponseEntity<DeactivateResponse> deactivateInsuredPerson(@PathVariable UUID insuredPersonId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        String role = getUserRole(authentication);
        DeactivateResponse response = insuredPersonService.deactivateInsuredPerson(authUserId, role, insuredPersonId);
        return ResponseEntity.ok(response);
    }

    private String getUserRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
    }
}
