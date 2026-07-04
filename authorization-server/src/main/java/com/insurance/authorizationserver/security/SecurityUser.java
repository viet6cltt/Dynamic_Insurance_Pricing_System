package com.insurance.authorizationserver.security;

import com.insurance.authorizationserver.model.AuthUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

public class SecurityUser implements UserDetails {
    private final AuthUser authUser;

    public SecurityUser(AuthUser authUser) {
        this.authUser = authUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleName()))
                .toList();
    }

    @Override
    public String getPassword() {
        return authUser.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return authUser.getEmail();
    }

    public String getName() {
        return authUser.getEmail();
    }

    public String getEmail() {
        return authUser.getEmail();
    }

    public UUID getId() {
        return authUser.getAuthUserId();
    }
}
