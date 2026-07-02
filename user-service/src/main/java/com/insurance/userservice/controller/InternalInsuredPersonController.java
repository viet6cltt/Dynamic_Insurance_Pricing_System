package com.insurance.userservice.controller;

import com.insurance.userservice.dto.InsuredPersonResponse;
import com.insurance.userservice.service.InsuredPersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/insured-persons")
@RequiredArgsConstructor
public class InternalInsuredPersonController {

    private final InsuredPersonService insuredPersonService;

    @GetMapping("/{insuredPersonId}")
    public ResponseEntity<InsuredPersonResponse> getInsuredPersonById(@PathVariable UUID insuredPersonId) {
        InsuredPersonResponse response = insuredPersonService.internalGetInsuredPerson(insuredPersonId);
        return ResponseEntity.ok(response);
    }
}
