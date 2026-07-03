package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.applicationpolicyservice.dto.EventEnvelope;
import com.insurance.applicationpolicyservice.model.InsuranceContract;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class PolicyEventFactory {

    private final ObjectMapper objectMapper;

    public PolicyEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope createContractEvent(InsuranceContract contract,
                                             String eventType,
                                             String correlationId,
                                             String causationId) {
        Map<String, Object> payloadValues = new HashMap<>();
        payloadValues.put("contractId", contract.getContractId());
        payloadValues.put("quoteId", contract.getQuoteId());
        payloadValues.put("customerId", contract.getApplicantUserId());
        payloadValues.put("insuredPersonId", contract.getInsuredPersonId());
        payloadValues.put("paymentId", contract.getPaymentId());
        payloadValues.put("paymentStatus", contract.getPaymentStatus());
        payloadValues.put("status", contract.getStatus().name());
        payloadValues.put("quotedPremium", contract.getQuotedPremium());
        payloadValues.put("currency", "VND");
        JsonNode payload = objectMapper.valueToTree(payloadValues);
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                "application-policy-service",
                "InsuranceContract",
                contract.getContractId(),
                correlationId,
                causationId,
                payload
        );
    }
}
