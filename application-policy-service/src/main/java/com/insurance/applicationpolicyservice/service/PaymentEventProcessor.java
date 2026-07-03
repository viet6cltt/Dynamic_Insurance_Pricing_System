package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.applicationpolicyservice.dto.ContractStatus;
import com.insurance.applicationpolicyservice.dto.EventEnvelope;
import com.insurance.applicationpolicyservice.dto.PaymentEventPayload;
import com.insurance.applicationpolicyservice.model.InsuranceContract;
import com.insurance.applicationpolicyservice.model.ProcessedEvent;
import com.insurance.applicationpolicyservice.repository.InsuranceContractRepository;
import com.insurance.applicationpolicyservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventProcessor {

    private static final String CONSUMER_NAME = "policy-service-payment-consumer";

    private final InsuranceContractRepository contractRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final PolicyEventFactory policyEventFactory;
    private final PolicyOutboxService policyOutboxService;
    private final ContractService contractService;

    @Transactional
    public void process(EventEnvelope event) {
        if (event == null || event.eventId() == null || event.eventType() == null) {
            throw new IllegalArgumentException("Invalid payment event");
        }
        if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
            log.info("Payment event {} already processed by {}", event.eventId(), CONSUMER_NAME);
            return;
        }
        if (!event.eventType().startsWith("payment.")) {
            markProcessed(event);
            return;
        }

        PaymentEventPayload payload = objectMapper.convertValue(event.payload(), PaymentEventPayload.class);
        InsuranceContract contract = contractRepository.findByIdForUpdate(payload.contractId())
                .orElseThrow(() -> new IllegalArgumentException("Contract not found for payment event: " + payload.contractId()));

        contract.setPaymentId(payload.paymentId());
        contract.setPaymentStatus(payload.status());

        boolean transitioned = switch (event.eventType()) {
            case "payment.created" -> false;
            case "payment.succeeded" -> handlePaymentSucceeded(contract);
            case "payment.failed", "payment.expired" -> handlePaymentFailed(contract);
            default -> {
                log.info("Ignoring unsupported payment event type {}", event.eventType());
                yield false;
            }
        };

        markProcessed(event);

        if (transitioned && contract.getStatus() == ContractStatus.ACTIVE) {
            policyOutboxService.enqueue(policyEventFactory.createContractEvent(
                    contract,
                    "policy.issued",
                    event.correlationId(),
                    event.eventId().toString()
            ));
        } else if (transitioned && contract.getStatus() == ContractStatus.PAYMENT_FAILED) {
            policyOutboxService.enqueue(policyEventFactory.createContractEvent(
                    contract,
                    "contract.payment_failed",
                    event.correlationId(),
                    event.eventId().toString()
            ));
        }
    }

    private boolean handlePaymentSucceeded(InsuranceContract contract) {
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            return false;
        }
        if (contract.getStatus() != ContractStatus.PAYMENT_PENDING) {
            log.info("Ignoring payment success for contract {} in status {}",
                    contract.getContractId(), contract.getStatus());
            return false;
        }

        contract.setStatus(ContractStatus.ACTIVE);
        contract.setIssuedAt(Instant.now());
        LocalDate effectiveDate = LocalDate.now();
        contract.setEffectiveDate(effectiveDate);
        contract.setExpiryDate(effectiveDate.plusYears(1).minusDays(1));
        contract.setExposureTime(BigDecimal.ONE);
        contractRepository.save(contract);
        contractService.updateExperienceSummary(contract);
        return true;
    }

    private boolean handlePaymentFailed(InsuranceContract contract) {
        if (contract.getStatus() == ContractStatus.PAYMENT_FAILED) {
            return false;
        }
        if (contract.getStatus() != ContractStatus.PAYMENT_PENDING) {
            log.info("Ignoring payment failure for contract {} in status {}",
                    contract.getContractId(), contract.getStatus());
            return false;
        }

        contract.setStatus(ContractStatus.PAYMENT_FAILED);
        contractRepository.save(contract);
        return true;
    }

    private void markProcessed(EventEnvelope event) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.eventId())
                .consumerName(CONSUMER_NAME)
                .eventType(event.eventType())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .build();
        processedEventRepository.save(processedEvent);
    }
}
