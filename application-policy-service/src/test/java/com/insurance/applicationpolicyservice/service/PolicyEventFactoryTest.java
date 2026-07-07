package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.applicationpolicyservice.dto.ContractStatus;
import com.insurance.applicationpolicyservice.model.InsuranceContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyEventFactoryTest {

    @Test
    void createContractEventCopiesContractFields() {
        InsuranceContract contract = contract();

        var envelope = new PolicyEventFactory(new ObjectMapper().registerModule(new JavaTimeModule()))
                .createContractEvent(contract, "contract.issued", "corr-1", "cause-1");

        assertEquals("contract.issued", envelope.eventType());
        assertEquals("InsuranceContract", envelope.aggregateType());
        assertEquals(contract.getContractId(), envelope.aggregateId());
        assertEquals("ACTIVE", envelope.payload().get("status").asText());
    }

    @Test
    void createContractExpiryReminderEventIncludesDaysUntilExpiry() {
        InsuranceContract contract = contract();

        var envelope = new PolicyEventFactory(new ObjectMapper().registerModule(new JavaTimeModule()))
                .createContractExpiryReminderEvent(contract, 7, null, null);

        assertEquals("contract.expiring_soon", envelope.eventType());
        assertEquals(7, envelope.payload().get("daysUntilExpiry").asInt());
    }

    private InsuranceContract contract() {
        return InsuranceContract.builder()
                .contractId(UUID.randomUUID())
                .applicantUserId(UUID.randomUUID())
                .insuredPersonId(UUID.randomUUID())
                .quoteId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .quotedPremium(new BigDecimal("1200000.00"))
                .purePremium(new BigDecimal("1000000.00"))
                .loadingRate(new BigDecimal("0.2000"))
                .sumInsured(new BigDecimal("100000000.00"))
                .status(ContractStatus.ACTIVE)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(1))
                .build();
    }
}
