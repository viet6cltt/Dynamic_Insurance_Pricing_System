package com.insurance.applicationpolicyservice.repository;

import com.insurance.applicationpolicyservice.model.InsuranceContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceContractRepository extends JpaRepository<InsuranceContract, UUID> {
    List<InsuranceContract> findByApplicantUserIdOrderByCreatedAtDesc(UUID applicantUserId);
    List<InsuranceContract> findByInsuredPersonIdAndProductType(UUID insuredPersonId, String productType);
    long countByInsuredPersonIdAndProductType(UUID insuredPersonId, String productType);
    boolean existsByInsuredPersonIdAndProductId(UUID insuredPersonId, UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InsuranceContract c where c.contractId = :contractId")
    Optional<InsuranceContract> findByIdForUpdate(@Param("contractId") UUID contractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from InsuranceContract c
            where c.status = com.insurance.applicationpolicyservice.dto.ContractStatus.ACTIVE
              and c.expiryDate < :today
            order by c.expiryDate asc
            """)
    List<InsuranceContract> findExpiredActiveContractsForUpdate(@Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from InsuranceContract c
            where c.status = com.insurance.applicationpolicyservice.dto.ContractStatus.ACTIVE
              and c.expiryDate = :targetDate
              and c.expiryReminderSentAt is null
            order by c.expiryDate asc
            """)
    List<InsuranceContract> findContractsNeedingExpiryReminderForUpdate(@Param("targetDate") LocalDate targetDate);
}
