package com.insurance.paymentservice.service;

import com.insurance.paymentservice.dto.EventEnvelope;
import com.insurance.paymentservice.model.Payment;
import com.insurance.paymentservice.model.PaymentStatus;
import com.insurance.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExpirationService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventFactory eventFactory;
    private final OutboxService outboxService;
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.payment.expiration-scan-delay-ms:60000}")
    @Transactional
    public int expirePendingPayments() {
        Instant now = Instant.now();
        List<Payment> expiredPayments = paymentRepository.findTop50ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                PaymentStatus.PENDING,
                now
        );

        for (Payment payment : expiredPayments) {
            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setExpiredAt(now);
            payment.setFailureReason("Payment expired before completion");
            EventEnvelope envelope = eventFactory.createPaymentEvent(payment, "payment.expired", null, null);
            outboxService.enqueue(envelope);
            paymentService.writeTransactionLog(payment, "INTERNAL", "PAYMENT_EXPIRED",
                    null, envelope.payload(), "SUCCESS", null);
        }

        if (!expiredPayments.isEmpty()) {
            log.info("Expired {} pending payments", expiredPayments.size());
        }
        return expiredPayments.size();
    }
}
