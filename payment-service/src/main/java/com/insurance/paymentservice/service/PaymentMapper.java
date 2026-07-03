package com.insurance.paymentservice.service;

import com.insurance.paymentservice.dto.PaymentResponse;
import com.insurance.paymentservice.model.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getContractId(),
                payment.getQuoteId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getProvider(),
                payment.getStatus().name(),
                payment.getExpiresAt(),
                payment.getPaidAt(),
                payment.getFailedAt(),
                payment.getExpiredAt(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
