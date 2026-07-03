package com.insurance.pricingservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.pricingservice.service.AiModelServiceClient;
import com.insurance.pricingservice.service.ApplicationPolicyServiceClient;
import com.insurance.pricingservice.service.ProductServiceClient;
import com.insurance.pricingservice.service.UserServiceClient;
import com.insurance.pricingservice.dto.*;
import com.insurance.pricingservice.dto.CreateQuoteRequest;
import com.insurance.pricingservice.dto.InternalCoveragePlanResponse;
import com.insurance.pricingservice.dto.InsuredPersonResponse;
import com.insurance.pricingservice.dto.UserProfileResponse;
import com.insurance.pricingservice.dto.ClaimHistorySummaryResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class PricingQuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private ProductServiceClient productServiceClient;

    @MockBean
    private ApplicationPolicyServiceClient applicationPolicyServiceClient;

    @MockBean
    private AiModelServiceClient aiModelServiceClient;

    @Test
    public void testCreateQuoteSuccess() throws Exception {
        UUID authUserId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID insuredPersonId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID coveragePlanId = UUID.randomUUID();

        // 1. Mock User Service
        UserProfileResponse buyerProfile = new UserProfileResponse(
                userId, authUserId, "Nguyen Van A", "0987654321", "buyer@gmail.com",
                "1990-01-01", "male", "123456789", "Ha Noi", "ACTIVE"
        );
        Mockito.when(userServiceClient.getUserProfileByAuthUserId(Mockito.any(UUID.class)))
                .thenReturn(buyerProfile);

        InsuredPersonResponse insuredPerson = new InsuredPersonResponse(
                insuredPersonId, userId, "Nguyen Van", "B", "1995-05-15",
                "male", "brother", "0912345678", "brother@gmail.com", "ACTIVE"
        );
        Mockito.when(userServiceClient.getInsuredPersonById(insuredPersonId))
                .thenReturn(insuredPerson);

        // Mock Policy Service
        Mockito.when(applicationPolicyServiceClient.getClaimHistorySummary(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(new ClaimHistorySummaryResponse(
                        0, 0.0, 5, 0.0, 0.0, 0.0, false, true
                ));

        // 2. Mock Product Service
        InternalCoveragePlanResponse coveragePlan = new InternalCoveragePlanResponse(
                coveragePlanId, productId, "HEALTH", "Standard Gold Plan",
                new BigDecimal("2500000.00"), new BigDecimal("150000000.00"), "ACTIVE"
        );
        Mockito.when(productServiceClient.getInternalCoveragePlan(coveragePlanId))
                .thenReturn(coveragePlan);

        // 3. Mock AI Model Service
        PortfolioModelOutput portfolioOutput = new PortfolioModelOutput(
                "configured", "portfolio-xgb-v1", "local", "portfolio_model", "latest", "1",
                new BigDecimal("3100000.00"), new BigDecimal("1.18"), new BigDecimal("1.15"),
                null, "Success"
        );
        HealthRiskModelOutput healthOutput = new HealthRiskModelOutput(
                "configured", "health-xgb-v1", "local", "health_model", "latest", "1",
                new BigDecimal("5000000.00"), new BigDecimal("3200000.00"), new BigDecimal("1.72"), new BigDecimal("1.42"),
                "HIGH", null, "Success"
        );
        HealthPricingPredictionResponse predictionResponse = new HealthPricingPredictionResponse(
                "v1.0", portfolioOutput, healthOutput, "Pricing Service / Rating Engine", "basePremium * portfolioRiskFactor * healthRiskFactor"
        );
        Mockito.when(aiModelServiceClient.predictHealthPricing(Mockito.any(HealthPricingPredictionRequest.class)))
                .thenReturn(predictionResponse);

        // Build sample input
        ObjectNode riskProfile = objectMapper.createObjectNode();
        riskProfile.put("bmi", 27.5);
        riskProfile.put("children", 1);
        riskProfile.put("smoker", true);
        riskProfile.put("bloodPressure", 130.0);
        riskProfile.put("exerciseFrequency", "LOW");
        riskProfile.put("preExistingCondition", false);
        riskProfile.put("occupationCode", "OFFICE_WORKER");

        CreateQuoteRequest request = new CreateQuoteRequest(
                insuredPersonId, productId, coveragePlanId, riskProfile
        );

        // Mock Security Context
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        authUserId.toString(), null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        // Run MockMvc post request
        mockMvc.perform(post("/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-USER-ID", authUserId.toString())
                        .header("X-USER-ROLE", "ROLE_USER")
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());
    }
}
