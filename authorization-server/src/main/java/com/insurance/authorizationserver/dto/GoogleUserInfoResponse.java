package com.insurance.authorizationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GoogleUserInfoResponse {
    private String sub;
    private String email;

    @JsonProperty("email_verified")
    private Boolean emailVerified;

    private String name;
    private String picture;
}
