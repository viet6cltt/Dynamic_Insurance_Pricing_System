package com.insurance.userservice.service;

import com.insurance.userservice.dto.*;
import com.insurance.userservice.model.InsuredPerson;
import com.insurance.userservice.model.UserProfile;
import com.insurance.userservice.repository.InsuredPersonRepository;
import com.insurance.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsuredPersonService {

    private final InsuredPersonRepository insuredPersonRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public InsuredPersonResponse createInsuredPerson(UUID authUserId, CreateInsuredPersonRequest request) {
        UserProfile owner = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found for authUserId: " + authUserId));

        InsuredPerson insuredPerson = InsuredPerson.builder()
                .ownerUserProfileId(owner.getUserProfileId())
                .linkedUserProfileId(request.linkedUserProfileId())
                .fullName(request.fullName())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .identityNumber(request.identityNumber())
                .relationshipToOwner(request.relationshipToOwner())
                .status("ACTIVE")
                .build();

        InsuredPerson saved = insuredPersonRepository.save(insuredPerson);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InsuredPersonResponse> getMyInsuredPersons(UUID authUserId, String status, int page, int size) {
        UserProfile owner = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found for authUserId: " + authUserId));

        Pageable pageable = PageRequest.of(page, size);
        Page<InsuredPerson> insuredPersonsPage;

        if (status != null && !status.isBlank()) {
            insuredPersonsPage = insuredPersonRepository.findByOwnerUserProfileIdAndStatus(owner.getUserProfileId(), status, pageable);
        } else {
            insuredPersonsPage = insuredPersonRepository.findByOwnerUserProfileId(owner.getUserProfileId(), pageable);
        }

        List<InsuredPersonResponse> items = insuredPersonsPage.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return new PagedResponse<>(
                items,
                insuredPersonsPage.getNumber(),
                insuredPersonsPage.getSize(),
                insuredPersonsPage.getTotalElements(),
                insuredPersonsPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public InsuredPersonResponse getInsuredPersonDetail(UUID authUserId, String userRole, UUID insuredPersonId) {
        InsuredPerson insuredPerson = insuredPersonRepository.findById(insuredPersonId)
                .orElseThrow(() -> new NoSuchElementException("Insured person not found for ID: " + insuredPersonId));

        validateOwnershipOrAdmin(authUserId, userRole, insuredPerson);

        return mapToResponse(insuredPerson);
    }

    @Transactional
    public InsuredPersonResponse updateInsuredPerson(UUID authUserId, String userRole, UUID insuredPersonId, UpdateInsuredPersonRequest request) {
        InsuredPerson insuredPerson = insuredPersonRepository.findById(insuredPersonId)
                .orElseThrow(() -> new NoSuchElementException("Insured person not found for ID: " + insuredPersonId));

        validateOwnershipOrAdmin(authUserId, userRole, insuredPerson);

        insuredPerson.setFullName(request.fullName());
        insuredPerson.setDateOfBirth(request.dateOfBirth());
        insuredPerson.setGender(request.gender());
        insuredPerson.setIdentityNumber(request.identityNumber());
        insuredPerson.setRelationshipToOwner(request.relationshipToOwner());
        insuredPerson.setLinkedUserProfileId(request.linkedUserProfileId());

        InsuredPerson saved = insuredPersonRepository.save(insuredPerson);
        return mapToResponse(saved);
    }

    @Transactional
    public DeactivateResponse deactivateInsuredPerson(UUID authUserId, String userRole, UUID insuredPersonId) {
        InsuredPerson insuredPerson = insuredPersonRepository.findById(insuredPersonId)
                .orElseThrow(() -> new NoSuchElementException("Insured person not found for ID: " + insuredPersonId));

        validateOwnershipOrAdmin(authUserId, userRole, insuredPerson);

        insuredPerson.setStatus("INACTIVE");
        insuredPersonRepository.save(insuredPerson);

        return new DeactivateResponse(
                insuredPersonId,
                "INACTIVE",
                "Insured person has been deactivated successfully."
        );
    }

    @Transactional(readOnly = true)
    public InsuredPersonResponse internalGetInsuredPerson(UUID insuredPersonId) {
        InsuredPerson insuredPerson = insuredPersonRepository.findById(insuredPersonId)
                .orElseThrow(() -> new NoSuchElementException("Insured person not found for ID: " + insuredPersonId));
        return mapToResponse(insuredPerson);
    }

    private void validateOwnershipOrAdmin(UUID authUserId, String userRole, InsuredPerson insuredPerson) {
        if (userRole != null && (userRole.contains("ADMIN") || userRole.contains("ROLE_ADMIN"))) {
            return;
        }

        UserProfile userProfile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found for authUserId: " + authUserId));

        if (!insuredPerson.getOwnerUserProfileId().equals(userProfile.getUserProfileId())) {
            throw new AccessDeniedException("You do not have permission to access this insured person record.");
        }
    }

    private InsuredPersonResponse mapToResponse(InsuredPerson insuredPerson) {
        return new InsuredPersonResponse(
                insuredPerson.getInsuredPersonId(),
                insuredPerson.getOwnerUserProfileId(),
                insuredPerson.getLinkedUserProfileId(),
                insuredPerson.getFullName(),
                insuredPerson.getDateOfBirth(),
                insuredPerson.getGender(),
                insuredPerson.getIdentityNumber(),
                insuredPerson.getRelationshipToOwner(),
                insuredPerson.getStatus(),
                insuredPerson.getCreatedAt(),
                insuredPerson.getUpdatedAt()
        );
    }
}
