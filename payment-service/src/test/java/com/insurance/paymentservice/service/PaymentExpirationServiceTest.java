package com.insurance.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.paymentservice.model.Payment;
import com.insurance.paymentservice.model.PaymentStatus;
import com.insurance.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private PaymentService paymentService;

    @Test
    void expirePendingPaymentsMarksExpiredAndWritesEvent() {
        Payment payment = pendingPayment();
        when(paymentRepository.findTop50ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(any(), any()))
                .thenReturn(List.of(payment));
        PaymentExpirationService service = new PaymentExpirationService(
                paymentRepository,
                new PaymentEventFactory(new ObjectMapper().registerModule(new JavaTimeModule())),
                outboxService,
                paymentService);

        int expired = service.expirePendingPayments();

        assertEquals(1, expired);
        assertEquals(PaymentStatus.EXPIRED, payment.getStatus());
        assertNotNull(payment.getExpiredAt());
        verify(outboxService).enqueue(any());
        verify(paymentService).writeTransactionLog(eq(payment), any(), any(), any(), any(), any(), any());
    }

    private Payment pendingPayment() {
        return Payment.builder()
                .paymentId(UUID.randomUUID())
                .contractId(UUID.randomUUID())
                .quoteId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .amount(new BigDecimal("1500000.00"))
                .currency("VND")
                .paymentMethod("MOCK")
                .provider("MOCK_PROVIDER")
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem")
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
    }
}
