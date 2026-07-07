package com.insurance.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.paymentservice.dto.CreateMockPaymentRequest;
import com.insurance.paymentservice.model.Payment;
import com.insurance.paymentservice.model.PaymentStatus;
import com.insurance.paymentservice.repository.PaymentRepository;
import com.insurance.paymentservice.repository.PaymentTransactionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionLogRepository logRepository;

    @Mock
    private OutboxService outboxService;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new PaymentService(
                paymentRepository,
                logRepository,
                new PaymentMapper(),
                new PaymentEventFactory(objectMapper),
                outboxService,
                objectMapper);
        ReflectionTestUtils.setField(service, "expirationMinutes", 15L);
    }

    @Test
    void createMockPaymentReturnsExistingPaymentForSameIdempotencyKey() {
        CreateMockPaymentRequest request = request("SUCCESS");
        Payment existing = payment(request, PaymentStatus.SUCCESS);
        when(paymentRepository.findByContractIdAndIdempotencyKey(request.contractId(), "idem-1"))
                .thenReturn(Optional.of(existing));

        var response = service.createMockPayment(request, " idem-1 ", "corr-1");

        assertEquals(existing.getPaymentId(), response.paymentId());
        verify(paymentRepository, never()).save(any());
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void createMockPaymentCreatesSuccessPaymentAndOutboxEvent() {
        CreateMockPaymentRequest request = request("SUCCESS");
        when(paymentRepository.findByContractIdAndIdempotencyKey(request.contractId(), "contract-" + request.contractId()))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentId(UUID.randomUUID());
            return payment;
        });

        var response = service.createMockPayment(request, null, "corr-1");

        assertEquals("SUCCESS", response.status());
        assertEquals("VND", response.currency());
        assertEquals("mock", response.paymentMethod());
        assertNotNull(response.paidAt());
        verify(logRepository).save(any());
        verify(outboxService).enqueue(any());
    }

    @Test
    void createMockPaymentCreatesFailedPaymentWithFailureReason() {
        CreateMockPaymentRequest request = request("FAILED");
        when(paymentRepository.findByContractIdAndIdempotencyKey(eq(request.contractId()), any()))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentId(UUID.randomUUID());
            return payment;
        });

        var response = service.createMockPayment(request, "failure", "corr-1");

        assertEquals("FAILED", response.status());
        assertEquals("Mock payment failure", response.failureReason());
        assertNotNull(response.failedAt());
    }

    @Test
    void createMockPaymentRejectsUnsupportedSimulateResult() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createMockPayment(request("CHARGEBACK"), null, null));
    }

    @Test
    void createMockPaymentRejectsNonPositiveAmount() {
        CreateMockPaymentRequest request = new CreateMockPaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, "VND", "MOCK", "SUCCESS");

        assertThrows(IllegalArgumentException.class, () -> service.createMockPayment(request, null, null));
    }

    @Test
    void getPaymentThrowsWhenMissing() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getPayment(paymentId));
    }

    private CreateMockPaymentRequest request(String simulateResult) {
        return new CreateMockPaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1500000.00"),
                " vnd ",
                " mock ",
                simulateResult);
    }

    private Payment payment(CreateMockPaymentRequest request, PaymentStatus status) {
        return Payment.builder()
                .paymentId(UUID.randomUUID())
                .contractId(request.contractId())
                .quoteId(request.quoteId())
                .customerId(request.customerId())
                .amount(request.amount())
                .currency("VND")
                .paymentMethod("MOCK")
                .provider("MOCK_PROVIDER")
                .status(status)
                .idempotencyKey("idem-1")
                .expiresAt(Instant.now().plusSeconds(900))
                .paidAt(status == PaymentStatus.SUCCESS ? Instant.now() : null)
                .failedAt(status == PaymentStatus.FAILED ? Instant.now() : null)
                .failureReason(status == PaymentStatus.FAILED ? "Mock payment failure" : null)
                .build();
    }
}
