package com.insurance.authorizationserver.security;

import com.insurance.authorizationserver.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AuthUserRepository authUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return authUserRepository.findByEmail(username)
                .or(() -> authUserRepository.findByPhoneNumber(username))
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email or phone number: " + username));
    }
}
