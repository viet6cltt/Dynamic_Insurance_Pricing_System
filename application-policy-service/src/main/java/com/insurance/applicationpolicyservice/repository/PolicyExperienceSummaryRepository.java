package com.insurance.applicationpolicyservice.repository;

import com.insurance.applicationpolicyservice.model.PolicyExperienceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyExperienceSummaryRepository extends JpaRepository<PolicyExperienceSummary, UUID> {
    Optional<PolicyExperienceSummary> findByInsuredPersonIdAndProductType(UUID insuredPersonId, String productType);
    Optional<PolicyExperienceSummary> findByPolicyholderUserIdAndProductType(UUID policyholderUserId, String productType);
    List<PolicyExperienceSummary> findByPolicyholderUserIdOrderByUpdatedAtDesc(UUID policyholderUserId);
    List<PolicyExperienceSummary> findByInsuredPersonIdInOrderByUpdatedAtDesc(List<UUID> insuredPersonIds);
}
