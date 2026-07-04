package com.insurance.authorizationserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCodeExchangeRequest {
    private String code;
    private String redirectUri;
}
