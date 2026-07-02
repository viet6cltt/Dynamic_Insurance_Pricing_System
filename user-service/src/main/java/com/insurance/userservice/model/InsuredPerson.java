package com.insurance.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "insured_persons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuredPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "insured_person_id", updatable = false, nullable = false)
    private UUID insuredPersonId;

    @Column(name = "owner_user_profile_id", nullable = false)
    private UUID ownerUserProfileId;

    @Column(name = "linked_user_profile_id")
    private UUID linkedUserProfileId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "identity_number", length = 20)
    private String identityNumber;

    @Column(name = "relationship_to_owner", length = 20)
    private String relationshipToOwner;

    @Column(name = "status", length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
