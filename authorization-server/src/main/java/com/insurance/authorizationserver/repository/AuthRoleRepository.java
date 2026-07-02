package com.insurance.authorizationserver.repository;

import com.insurance.authorizationserver.model.AuthRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthRoleRepository extends JpaRepository<AuthRole, UUID> {
    Optional<AuthRole> findByRoleName(String roleName);
}
