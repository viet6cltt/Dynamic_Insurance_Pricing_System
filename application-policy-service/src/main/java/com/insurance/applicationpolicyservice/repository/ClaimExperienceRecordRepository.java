package com.insurance.applicationpolicyservice.repository;

import com.insurance.applicationpolicyservice.model.ClaimExperienceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimExperienceRecordRepository extends JpaRepository<ClaimExperienceRecord, UUID> {
    List<ClaimExperienceRecord> findByInsuredPersonIdAndProductType(UUID insuredPersonId, String productType);
}
