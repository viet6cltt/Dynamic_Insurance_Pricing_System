package com.insurance.authorizationserver.service;

import com.insurance.authorizationserver.dto.GoogleCodeExchangeRequest;
import com.insurance.authorizationserver.dto.GoogleTokenResponse;
import com.insurance.authorizationserver.dto.GoogleUserInfoResponse;
import com.insurance.authorizationserver.dto.LoginResponse;
import com.insurance.authorizationserver.model.AuthUser;
import com.insurance.authorizationserver.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final AuthUserService authUserService;
    private final TokenService tokenService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.google.client-id}")
    private String googleClientId;

    @Value("${app.google.client-secret}")
    private String googleClientSecret;

    public LoginResponse exchangeCode(GoogleCodeExchangeRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Google authorization code is required");
        }
        if (request.getRedirectUri() == null || request.getRedirectUri().isBlank()) {
            throw new IllegalArgumentException("Google redirectUri is required");
        }
        if (googleClientSecret == null || googleClientSecret.isBlank()
                || "your-google-client-secret".equals(googleClientSecret)) {
            throw new IllegalStateException("GOOGLE_CLIENT_SECRET is not configured on authorization-server");
        }

        GoogleTokenResponse tokenResponse = exchangeGoogleToken(request);
        GoogleUserInfoResponse userInfo = fetchGoogleUserInfo(tokenResponse.getAccessToken());

        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            throw new IllegalStateException("Google account did not return an email");
        }

        AuthUser authUser = authUserService.findOrCreateGoogleUser(
                userInfo.getEmail(),
                userInfo.getName(),
                Boolean.TRUE.equals(userInfo.getEmailVerified()));

        return buildLoginResponse(new SecurityUser(authUser));
    }

    private GoogleTokenResponse exchangeGoogleToken(GoogleCodeExchangeRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", request.getCode());
        body.add("client_id", googleClientId);
        body.add("client_secret", googleClientSecret);
        body.add("redirect_uri", request.getRedirectUri());

        try {
            GoogleTokenResponse response = restTemplate.postForObject(
                    GOOGLE_TOKEN_URL,
                    new HttpEntity<>(body, headers),
                    GoogleTokenResponse.class);
            if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
                throw new IllegalStateException("Google token response did not include access_token");
            }
            return response;
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Failed to exchange Google authorization code: "
                    + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to exchange Google authorization code", e);
        }
    }

    private GoogleUserInfoResponse fetchGoogleUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<GoogleUserInfoResponse> entity = restTemplate.exchange(
                    GOOGLE_USER_INFO_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    GoogleUserInfoResponse.class);
            GoogleUserInfoResponse response = entity.getBody();
            if (response == null) {
                throw new IllegalStateException("Google userinfo response is empty");
            }
            return response;
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Failed to fetch Google user info: "
                    + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch Google user info", e);
        }
    }

    private LoginResponse buildLoginResponse(SecurityUser user) {
        String role = user.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER");

        return LoginResponse.builder()
                .accessToken(tokenService.issue(user))
                .refreshToken(tokenService.issueRefreshToken(user))
                .tokenType("Bearer")
                .expiresIn(tokenService.getTokenValiditySeconds())
                .refreshExpiresIn(tokenService.getRefreshTokenValiditySeconds())
                .role(role)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
