package com.insurance.userservice.service;

import com.insurance.userservice.dto.CreateInsuredPersonRequest;
import com.insurance.userservice.dto.UpdateInsuredPersonRequest;
import com.insurance.userservice.model.InsuredPerson;
import com.insurance.userservice.model.UserProfile;
import com.insurance.userservice.repository.InsuredPersonRepository;
import com.insurance.userservice.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsuredPersonServiceTest {

    @Mock
    private InsuredPersonRepository insuredPersonRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private InsuredPersonService service;

    @Test
    void createInsuredPersonUsesCurrentUserProfileAsOwner() {
        UUID authUserId = UUID.randomUUID();
        UserProfile owner = owner(authUserId);
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(owner));
        when(insuredPersonRepository.save(any(InsuredPerson.class))).thenAnswer(invocation -> {
            InsuredPerson person = invocation.getArgument(0);
            person.setInsuredPersonId(UUID.randomUUID());
            return person;
        });

        var response = service.createInsuredPerson(authUserId, new CreateInsuredPersonRequest(
                "Nguyen Van B",
                LocalDate.of(2010, 5, 10),
                "MALE",
                "CHILD-001",
                "CHILD",
                null));

        assertEquals(owner.getUserProfileId(), response.ownerUserProfileId());
        assertEquals("ACTIVE", response.status());
        assertEquals("Nguyen Van B", response.fullName());
    }

    @Test
    void getMyInsuredPersonsFiltersByStatusWhenProvided() {
        UUID authUserId = UUID.randomUUID();
        UserProfile owner = owner(authUserId);
        InsuredPerson person = person(owner.getUserProfileId());
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(owner));
        when(insuredPersonRepository.findByOwnerUserProfileIdAndStatus(eq(owner.getUserProfileId()), eq("ACTIVE"), any()))
                .thenReturn(new PageImpl<>(List.of(person)));

        var response = service.getMyInsuredPersons(authUserId, "ACTIVE", 0, 20);

        assertEquals(1, response.items().size());
        assertEquals("ACTIVE", response.items().getFirst().status());
    }

    @Test
    void getInsuredPersonDetailAllowsOwner() {
        UUID authUserId = UUID.randomUUID();
        UserProfile owner = owner(authUserId);
        InsuredPerson person = person(owner.getUserProfileId());
        when(insuredPersonRepository.findById(person.getInsuredPersonId())).thenReturn(Optional.of(person));
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(owner));

        var response = service.getInsuredPersonDetail(authUserId, "ROLE_USER", person.getInsuredPersonId());

        assertEquals(person.getInsuredPersonId(), response.insuredPersonId());
    }

    @Test
    void getInsuredPersonDetailAllowsAdminWithoutOwnershipLookup() {
        InsuredPerson person = person(UUID.randomUUID());
        when(insuredPersonRepository.findById(person.getInsuredPersonId())).thenReturn(Optional.of(person));

        var response = service.getInsuredPersonDetail(UUID.randomUUID(), "ROLE_ADMIN", person.getInsuredPersonId());

        assertEquals(person.getInsuredPersonId(), response.insuredPersonId());
    }

    @Test
    void getInsuredPersonDetailRejectsNonOwner() {
        UUID authUserId = UUID.randomUUID();
        UserProfile owner = owner(authUserId);
        InsuredPerson person = person(UUID.randomUUID());
        when(insuredPersonRepository.findById(person.getInsuredPersonId())).thenReturn(Optional.of(person));
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(owner));

        assertThrows(AccessDeniedException.class,
                () -> service.getInsuredPersonDetail(authUserId, "ROLE_USER", person.getInsuredPersonId()));
    }

    @Test
    void updateInsuredPersonPersistsChanges() {
        UUID authUserId = UUID.randomUUID();
        UserProfile owner = owner(authUserId);
        InsuredPerson person = person(owner.getUserProfileId());
        when(insuredPersonRepository.findById(person.getInsuredPersonId())).thenReturn(Optional.of(person));
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(owner));
        when(insuredPersonRepository.save(any(InsuredPerson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateInsuredPerson(authUserId, "ROLE_USER", person.getInsuredPersonId(),
                new UpdateInsuredPersonRequest("Updated Name", LocalDate.of(2011, 1, 1),
                        "FEMALE", "NEW-ID", "CHILD", UUID.randomUUID()));

        assertEquals("Updated Name", response.fullName());
        assertEquals("NEW-ID", response.identityNumber());
    }

    @Test
    void deactivateInsuredPersonSetsInactiveStatus() {
        UUID authUserId = UUID.randomUUID();
        UserProfile owner = owner(authUserId);
        InsuredPerson person = person(owner.getUserProfileId());
        when(insuredPersonRepository.findById(person.getInsuredPersonId())).thenReturn(Optional.of(person));
        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(owner));

        var response = service.deactivateInsuredPerson(authUserId, "ROLE_USER", person.getInsuredPersonId());

        assertEquals("INACTIVE", response.status());
        verify(insuredPersonRepository).save(person);
    }

    @Test
    void internalGetInsuredPersonThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(insuredPersonRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.internalGetInsuredPerson(id));
    }

    private UserProfile owner(UUID authUserId) {
        return UserProfile.builder()
                .userProfileId(UUID.randomUUID())
                .authUserId(authUserId)
                .email("owner@example.com")
                .customerStatus("ACTIVE")
                .build();
    }

    private InsuredPerson person(UUID ownerUserProfileId) {
        return InsuredPerson.builder()
                .insuredPersonId(UUID.randomUUID())
                .ownerUserProfileId(ownerUserProfileId)
                .fullName("Nguyen Van B")
                .dateOfBirth(LocalDate.of(2010, 5, 10))
                .gender("MALE")
                .identityNumber("CHILD-001")
                .relationshipToOwner("CHILD")
                .status("ACTIVE")
                .build();
    }
}
