package com.insurance.applicationpolicyservice.controller;

import com.insurance.applicationpolicyservice.dto.CreateContractRequest;
import com.insurance.applicationpolicyservice.dto.ContractResponse;
import com.insurance.applicationpolicyservice.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping
    public ResponseEntity<ContractResponse> createContract(@RequestBody CreateContractRequest request) {
        String buyerUserIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        UUID buyerUserId = UUID.fromString(buyerUserIdStr);
        ContractResponse response = contractService.createContract(buyerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ContractResponse> getContractById(@PathVariable("contractId") UUID contractId) {
        ContractResponse response = contractService.getContractById(contractId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<ContractResponse>> getMyContracts() {
        String buyerUserIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        UUID buyerUserId = UUID.fromString(buyerUserIdStr);
        List<ContractResponse> response = contractService.getMyContracts(buyerUserId);
        return ResponseEntity.ok(response);
    }
}
