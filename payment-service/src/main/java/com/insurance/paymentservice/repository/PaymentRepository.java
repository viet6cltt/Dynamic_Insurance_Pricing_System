package com.insurance.paymentservice.repository;

import com.insurance.paymentservice.model.Payment;
import com.insurance.paymentservice.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByContractIdAndIdempotencyKey(UUID contractId, String idempotencyKey);
    List<Payment> findTop50ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(PaymentStatus status, Instant now);
}
