package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.notificationservice.dto.EventEnvelope;
import com.insurance.notificationservice.dto.UserProfileResponse;
import com.insurance.notificationservice.model.ChannelPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDefinitionFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private NotificationDefinitionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new NotificationDefinitionFactory(objectMapper);
    }

    @ParameterizedTest
    @CsvSource({
            "payment.created,IN_APP_AND_EMAIL,payment-created",
            "payment.succeeded,IN_APP_AND_EMAIL,payment-succeeded",
            "payment.failed,IN_APP_AND_EMAIL,payment-failed",
            "payment.expired,IN_APP_AND_EMAIL,payment-expired",
            "payment.cancelled,IN_APP_AND_EMAIL,payment-cancelled",
            "policy.issued,IN_APP_AND_EMAIL,policy-issued",
            "contract.expiring_soon,IN_APP_AND_EMAIL,contract-expiring-soon",
            "contract.expired,IN_APP_AND_EMAIL,contract-expired",
            "quote.generated,IN_APP_ONLY,",
            "quote.expired,IN_APP_ONLY,"
    })
    void createBuildsDefinitionForSupportedEvents(
            String eventType,
            ChannelPolicy channelPolicy,
            String templateName) {
        Optional<NotificationDefinition> definition = factory.create(event(eventType, validPayload()), profile("Nguyen Van A"));

        assertThat(definition).isPresent();
        assertThat(definition.get().title()).isNotBlank();
        assertThat(definition.get().message()).isNotBlank();
        assertThat(definition.get().channelPolicy()).isEqualTo(channelPolicy);
        assertThat(definition.get().templateName()).isEqualTo(templateName == null ? null : templateName);
        assertThat(definition.get().templateModel())
                .containsEntry("customerName", "Nguyen Van A")
                .containsEntry("eventType", eventType)
                .containsKey("formattedAmount");
    }

    @Test
    void createReturnsEmptyForInvalidOrUnsupportedEvents() {
        assertThat(factory.create(null, profile("Nguyen Van A"))).isEmpty();
        assertThat(factory.create(event(null, validPayload()), profile("Nguyen Van A"))).isEmpty();
        assertThat(factory.create(event("unknown.event", validPayload()), profile("Nguyen Van A"))).isEmpty();
        assertThat(factory.create(event("payment.created", null), profile("Nguyen Van A"))).isEmpty();
    }

    @Test
    void createUsesFallbackCustomerNameAndQuotedPremiumAmount() {
        var payload = objectMapper.createObjectNode()
                .put("quoteId", UUID.randomUUID().toString())
                .put("quotedPremium", "1234567")
                .put("currency", "VND");

        NotificationDefinition definition = factory.create(event("quote.generated", payload), profile(" "))
                .orElseThrow();

        assertThat(definition.templateModel()).containsEntry("customerName", "Quy khach");
        assertThat(definition.templateModel().get("formattedAmount").toString()).contains("1.234.567 VND");
    }

    @Test
    void createUsesFinalPremiumWhenAmountAndQuotedPremiumAreMissing() {
        var payload = objectMapper.createObjectNode()
                .put("quoteId", UUID.randomUUID().toString())
                .put("finalPremium", "2000000");

        NotificationDefinition definition = factory.create(event("quote.generated", payload), profile("Buyer"))
                .orElseThrow();

        assertThat(definition.templateModel().get("formattedAmount").toString()).contains("2.000.000 VND");
    }

    @Test
    void createIgnoresInvalidAmount() {
        var payload = validPayload().put("amount", "not-a-number");

        NotificationDefinition definition = factory.create(event("payment.succeeded", payload), profile("Buyer"))
                .orElseThrow();

        assertThat(definition.templateModel()).containsEntry("formattedAmount", "");
    }

    private EventEnvelope event(String eventType, com.fasterxml.jackson.databind.JsonNode payload) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                "test",
                "Aggregate",
                UUID.randomUUID(),
                "corr-1",
                null,
                payload);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode validPayload() {
        return objectMapper.createObjectNode()
                .put("customerId", UUID.randomUUID().toString())
                .put("contractId", UUID.randomUUID().toString())
                .put("quoteId", UUID.randomUUID().toString())
                .put("daysUntilExpiry", "7")
                .put("failureReason", "insufficient funds")
                .put("amount", "1000000")
                .put("currency", "VND");
    }

    private UserProfileResponse profile(String fullName) {
        return new UserProfileResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "buyer@example.com",
                "0900000000",
                fullName,
                "ID-1",
                LocalDate.of(1990, 1, 1),
                "MALE",
                "ACTIVE",
                Instant.now(),
                Instant.now());
    }
}
