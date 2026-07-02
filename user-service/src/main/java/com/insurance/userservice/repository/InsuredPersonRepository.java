package com.insurance.userservice.repository;

import com.insurance.userservice.model.InsuredPerson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InsuredPersonRepository extends JpaRepository<InsuredPerson, UUID> {
    Page<InsuredPerson> findByOwnerUserProfileIdAndStatus(UUID ownerUserProfileId, String status, Pageable pageable);
    Page<InsuredPerson> findByOwnerUserProfileId(UUID ownerUserProfileId, Pageable pageable);
}
