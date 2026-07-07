package com.insurance.userservice.service;

import com.insurance.userservice.dto.CreateUserProfileRequest;
import com.insurance.userservice.dto.UpdateUserProfileRequest;
import com.insurance.userservice.model.UserProfile;
import com.insurance.userservice.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService service;

    @Test
    void createUserProfileDefaultsStatusAndPersistsProfile() {
        UUID authUserId = UUID.randomUUID();
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                authUserId,
                "buyer@example.com",
                "0900000000",
                "Nguyen Van A",
                "ID-001",
                LocalDate.of(1990, 1, 1),
                "MALE");

        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());
        when(userProfileRepository.existsByIdentityNumber("ID-001")).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0);
            profile.setUserProfileId(UUID.randomUUID());
            return profile;
        });

        var response = service.createUserProfile(request);

        assertEquals(authUserId, response.authUserId());
        assertEquals("ACTIVE", response.customerStatus());
        assertEquals("Nguyen Van A", response.fullName());
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void createUserProfileRejectsDuplicateAuthUserId() {
        UUID authUserId = UUID.randomUUID();
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                authUserId, "buyer@example.com", null, "Nguyen Van A", null, null, null);
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(profile(authUserId)));

        assertThrows(IllegalArgumentException.class, () -> service.createUserProfile(request));
    }

    @Test
    void createUserProfileRejectsDuplicateIdentityNumber() {
        UUID authUserId = UUID.randomUUID();
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                authUserId, "buyer@example.com", null, "Nguyen Van A", "ID-001", null, null);
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());
        when(userProfileRepository.existsByIdentityNumber("ID-001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createUserProfile(request));
    }

    @Test
    void getUserProfileByAuthUserIdMapsStoredProfile() {
        UUID authUserId = UUID.randomUUID();
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(profile(authUserId)));

        var response = service.getUserProfileByAuthUserId(authUserId);

        assertEquals(authUserId, response.authUserId());
        assertEquals("buyer@example.com", response.email());
    }

    @Test
    void getUserProfileByIdThrowsWhenMissing() {
        UUID userProfileId = UUID.randomUUID();
        when(userProfileRepository.findById(userProfileId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getUserProfileById(userProfileId));
    }

    @Test
    void updateCurrentUserProfileRejectsIdentityNumberOwnedByAnotherProfile() {
        UUID authUserId = UUID.randomUUID();
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(profile(authUserId)));
        when(userProfileRepository.existsByIdentityNumber("ID-999")).thenReturn(true);

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "0911111111", "Updated", "ID-999", LocalDate.of(1991, 2, 2), "FEMALE");

        assertThrows(IllegalArgumentException.class, () -> service.updateCurrentUserProfile(authUserId, request));
    }

    @Test
    void updateCurrentUserProfileSavesChangedFields() {
        UUID authUserId = UUID.randomUUID();
        UserProfile existing = profile(authUserId);
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "0911111111", "Tran Thi B", "ID-001", LocalDate.of(1991, 2, 2), "FEMALE");

        var response = service.updateCurrentUserProfile(authUserId, request);

        assertEquals("Tran Thi B", response.fullName());
        assertEquals("0911111111", response.phoneNumber());
        assertEquals("FEMALE", response.gender());
    }

    private UserProfile profile(UUID authUserId) {
        return UserProfile.builder()
                .userProfileId(UUID.randomUUID())
                .authUserId(authUserId)
                .email("buyer@example.com")
                .phoneNumber("0900000000")
                .fullName("Nguyen Van A")
                .identityNumber("ID-001")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("MALE")
                .customerStatus("ACTIVE")
                .build();
    }
}
