package com.insurance.authorizationserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {
    private String email;
    private String phoneNumber;
    private String password;

    // User Profile details
    private String fullName;
    private String identityNumber;
    private LocalDate dateOfBirth;
    private String gender;

    private String role; // "USER", "ADMIN", "MANAGER"
}
