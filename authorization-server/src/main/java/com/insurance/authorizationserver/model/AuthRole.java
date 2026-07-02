package com.insurance.authorizationserver.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "auth_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "role_id", updatable = false, nullable = false)
    private UUID roleId;

    @Column(name = "role_name", unique = true, nullable = false, length = 50)
    private String roleName;
}
