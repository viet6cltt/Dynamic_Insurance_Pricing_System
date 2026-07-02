package com.insurance.userservice.service;

import com.insurance.userservice.dto.CreateUserProfileRequest;
import com.insurance.userservice.dto.UpdateUserProfileRequest;
import com.insurance.userservice.dto.UserProfileResponse;
import com.insurance.userservice.model.UserProfile;
import com.insurance.userservice.repository.UserProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    @Transactional
    public UserProfileResponse createUserProfile(CreateUserProfileRequest request) {
        if (userProfileRepository.findByAuthUserId(request.authUserId()).isPresent()) {
            throw new IllegalArgumentException("User profile with authUserId already exists: " + request.authUserId());
        }
        if (request.identityNumber() != null && userProfileRepository.existsByIdentityNumber(request.identityNumber())) {
            throw new IllegalArgumentException("User profile with identityNumber already exists: " + request.identityNumber());
        }

        UserProfile userProfile = UserProfile.builder()
                .authUserId(request.authUserId())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .fullName(request.fullName())
                .identityNumber(request.identityNumber())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .customerStatus("ACTIVE")
                .build();

        UserProfile saved = userProfileRepository.save(userProfile);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileByAuthUserId(UUID authUserId) {
        UserProfile userProfile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found for authUserId: " + authUserId));
        return mapToResponse(userProfile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileById(UUID userProfileId) {
        UserProfile userProfile = userProfileRepository.findById(userProfileId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found for ID: " + userProfileId));
        return mapToResponse(userProfile);
    }

    @Transactional
    public UserProfileResponse updateCurrentUserProfile(UUID authUserId, UpdateUserProfileRequest request) {
        UserProfile userProfile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found for authUserId: " + authUserId));

        if (request.identityNumber() != null && !request.identityNumber().equals(userProfile.getIdentityNumber())
                && userProfileRepository.existsByIdentityNumber(request.identityNumber())) {
            throw new IllegalArgumentException("Identity number already in use: " + request.identityNumber());
        }

        userProfile.setPhoneNumber(request.phoneNumber());
        userProfile.setFullName(request.fullName());
        userProfile.setIdentityNumber(request.identityNumber());
        userProfile.setDateOfBirth(request.dateOfBirth());
        userProfile.setGender(request.gender());

        UserProfile saved = userProfileRepository.save(userProfile);
        return mapToResponse(saved);
    }

    private UserProfileResponse mapToResponse(UserProfile userProfile) {
        return new UserProfileResponse(
                userProfile.getUserProfileId(),
                userProfile.getAuthUserId(),
                userProfile.getEmail(),
                userProfile.getPhoneNumber(),
                userProfile.getFullName(),
                userProfile.getIdentityNumber(),
                userProfile.getDateOfBirth(),
                userProfile.getGender(),
                userProfile.getCustomerStatus(),
                userProfile.getCreatedAt(),
                userProfile.getUpdatedAt()
        );
    }
}
