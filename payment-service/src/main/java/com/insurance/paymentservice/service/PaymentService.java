package com.insurance.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.paymentservice.dto.CreateMockPaymentRequest;
import com.insurance.paymentservice.dto.EventEnvelope;
import com.insurance.paymentservice.dto.PaymentResponse;
import com.insurance.paymentservice.model.Payment;
import com.insurance.paymentservice.model.PaymentStatus;
import com.insurance.paymentservice.model.PaymentTransactionLog;
import com.insurance.paymentservice.repository.PaymentRepository;
import com.insurance.paymentservice.repository.PaymentTransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionLogRepository logRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentEventFactory eventFactory;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.expiration-minutes:15}")
    private long expirationMinutes;

    @Transactional
    public PaymentResponse createMockPayment(CreateMockPaymentRequest request,
                                             String idempotencyKey,
                                             String correlationId) {
        validateCreateRequest(request);
        String normalizedKey = normalizeIdempotencyKey(request.contractId(), idempotencyKey);

        return paymentRepository.findByContractIdAndIdempotencyKey(request.contractId(), normalizedKey)
                .map(payment -> {
                    log.info("Returning existing payment {} for contract {} and idempotency key {}",
                            payment.getPaymentId(), payment.getContractId(), normalizedKey);
                    return paymentMapper.toResponse(payment);
                })
                .orElseGet(() -> createNewPayment(request, normalizedKey, correlationId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found with ID: " + paymentId));
        return paymentMapper.toResponse(payment);
    }

    private PaymentResponse createNewPayment(CreateMockPaymentRequest request,
                                             String idempotencyKey,
                                             String correlationId) {
        Instant now = Instant.now();
        PaymentStatus requestedStatus = parseSimulateResult(request.simulateResult());
        Payment payment = Payment.builder()
                .contractId(request.contractId())
                .quoteId(request.quoteId())
                .customerId(request.customerId())
                .amount(request.amount())
                .currency(defaultIfBlank(request.currency(), "VND").toUpperCase(Locale.ROOT))
                .paymentMethod(defaultIfBlank(request.paymentMethod(), "MOCK"))
                .provider("MOCK_PROVIDER")
                .status(requestedStatus)
                .idempotencyKey(idempotencyKey)
                .externalTransactionId("mock-" + UUID.randomUUID())
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .build();

        if (requestedStatus == PaymentStatus.SUCCESS) {
            payment.setPaidAt(now);
        } else if (requestedStatus == PaymentStatus.FAILED) {
            payment.setFailedAt(now);
            payment.setFailureReason("Mock payment failure");
        }

        Payment saved = paymentRepository.save(payment);
        PaymentResponse response = paymentMapper.toResponse(saved);
        writeTransactionLog(saved, "INBOUND_REQUEST", "CREATE_MOCK_PAYMENT",
                objectMapper.valueToTree(request), objectMapper.valueToTree(response), "SUCCESS", null);

        String eventType = eventTypeForStatus(saved.getStatus());
        EventEnvelope envelope = eventFactory.createPaymentEvent(saved, eventType, correlationId, null);
        outboxService.enqueue(envelope);

        log.info("Created mock payment {} for contract {} with status {}",
                saved.getPaymentId(), saved.getContractId(), saved.getStatus());
        return response;
    }

    void writeTransactionLog(Payment payment,
                             String direction,
                             String eventType,
                             JsonNode requestPayload,
                             JsonNode responsePayload,
                             String status,
                             String errorMessage) {
        PaymentTransactionLog transactionLog = PaymentTransactionLog.builder()
                .payment(payment)
                .direction(direction)
                .eventType(eventType)
                .requestPayload(requestPayload)
                .responsePayload(responsePayload)
                .status(status)
                .errorMessage(errorMessage)
                .build();
        logRepository.save(transactionLog);
    }

    private void validateCreateRequest(CreateMockPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request is required");
        }
        if (request.contractId() == null) {
            throw new IllegalArgumentException("contractId is required");
        }
        if (request.quoteId() == null) {
            throw new IllegalArgumentException("quoteId is required");
        }
        if (request.customerId() == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }

    private PaymentStatus parseSimulateResult(String simulateResult) {
        String value = defaultIfBlank(simulateResult, "SUCCESS").toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SUCCESS" -> PaymentStatus.SUCCESS;
            case "FAILED" -> PaymentStatus.FAILED;
            case "PENDING" -> PaymentStatus.PENDING;
            default -> throw new IllegalArgumentException("Unsupported simulateResult: " + simulateResult);
        };
    }

    private String eventTypeForStatus(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> "payment.succeeded";
            case FAILED -> "payment.failed";
            case PENDING -> "payment.created";
            case EXPIRED -> "payment.expired";
            case CANCELLED -> "payment.cancelled";
        };
    }

    private String normalizeIdempotencyKey(UUID contractId, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            return idempotencyKey.trim();
        }
        return "contract-" + contractId;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
