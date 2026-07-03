package com.insurance.paymentservice.repository;

import com.insurance.paymentservice.model.PaymentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLog, UUID> {
}
