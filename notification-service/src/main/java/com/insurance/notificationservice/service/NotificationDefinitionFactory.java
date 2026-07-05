package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.notificationservice.dto.EventEnvelope;
import com.insurance.notificationservice.dto.UserProfileResponse;
import com.insurance.notificationservice.model.ChannelPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class NotificationDefinitionFactory {

    private final ObjectMapper objectMapper;
    private final NumberFormat currencyFormatter = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public NotificationDefinitionFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<NotificationDefinition> create(EventEnvelope event, UserProfileResponse profile) {
        if (event == null || event.eventType() == null || event.payload() == null) {
            return Optional.empty();
        }

        JsonNode payload = event.payload();
        Map<String, Object> model = baseModel(event, profile);

        return switch (event.eventType()) {
            case "payment.created" -> Optional.of(definition(
                    "Yeu cau thanh toan da duoc tao",
                    "Yeu cau thanh toan " + amount(payload) + " cho hop dong " + text(payload, "contractId")
                            + " da duoc tao.",
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Yeu cau thanh toan bao hiem da duoc tao",
                    "payment-created",
                    model));
            case "payment.succeeded" -> Optional.of(definition(
                    "Thanh toan thanh cong",
                    "Thanh toan " + amount(payload) + " cho hop dong " + text(payload, "contractId")
                            + " da thanh cong.",
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Thanh toan bao hiem thanh cong",
                    "payment-succeeded",
                    model));
            case "payment.failed" -> Optional.of(definition(
                    "Thanh toan that bai",
                    "Thanh toan cho hop dong " + text(payload, "contractId") + " that bai. "
                            + text(payload, "failureReason"),
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Thanh toan bao hiem that bai",
                    "payment-failed",
                    model));
            case "payment.expired" -> Optional.of(definition(
                    "Yeu cau thanh toan da het han",
                    "Yeu cau thanh toan cho hop dong " + text(payload, "contractId") + " da het han.",
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Yeu cau thanh toan da het han",
                    "payment-expired",
                    model));
            case "payment.cancelled" -> Optional.of(definition(
                    "Thanh toan da bi huy",
                    "Thanh toan cho hop dong " + text(payload, "contractId") + " da bi huy.",
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Thanh toan bao hiem da bi huy",
                    "payment-cancelled",
                    model));
            case "policy.issued" -> Optional.of(definition(
                    "Hop dong bao hiem da co hieu luc",
                    "Hop dong " + text(payload, "contractId") + " da duoc phat hanh va co hieu luc.",
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Hop dong bao hiem da duoc phat hanh",
                    "policy-issued",
                    model));
            case "contract.expiring_soon" -> Optional.of(definition(
                    "Hop dong sap het han",
                    "Hop dong " + text(payload, "contractId") + " se het han sau " + text(payload, "daysUntilExpiry")
                            + " ngay.",
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Hop dong bao hiem sap het han",
                    "contract-expiring-soon",
                    model));
            case "contract.expired" -> Optional.of(definition(
                    "Hop dong da het han",
                    "Hop dong " + text(payload, "contractId") + " da het han.",
                    ChannelPolicy.IN_APP_AND_EMAIL,
                    "Hop dong bao hiem da het han",
                    "contract-expired",
                    model));
            case "quote.generated" -> Optional.of(definition(
                    "Bao gia da duoc tao",
                    "Bao gia " + text(payload, "quoteId") + " voi phi bao hiem " + amount(payload) + " da san sang.",
                    ChannelPolicy.IN_APP_ONLY,
                    null,
                    null,
                    model));
            case "quote.expired" -> Optional.of(definition(
                    "Bao gia da het han",
                    "Bao gia " + text(payload, "quoteId") + " da het han.",
                    ChannelPolicy.IN_APP_ONLY,
                    null,
                    null,
                    model));
            default -> Optional.empty();
        };
    }

    private NotificationDefinition definition(String title,
            String message,
            ChannelPolicy channelPolicy,
            String emailSubject,
            String templateName,
            Map<String, Object> model) {
        return new NotificationDefinition(title, message, channelPolicy, emailSubject, templateName, model);
    }

    private Map<String, Object> baseModel(EventEnvelope event, UserProfileResponse profile) {
        Map<String, Object> model = objectMapper.convertValue(event.payload(), Map.class);
        model = new HashMap<>(model);
        model.put("customerName", defaultIfBlank(profile.fullName(), "Quy khach"));
        model.put("eventType", event.eventType());
        model.put("occurredAt", event.occurredAt());
        model.put("formattedAmount", amount(event.payload()));
        return model;
    }

    private String amount(JsonNode payload) {
        String currency = defaultIfBlank(text(payload, "currency"), "VND");
        BigDecimal value = decimal(payload, "amount")
                .or(() -> decimal(payload, "quotedPremium"))
                .or(() -> decimal(payload, "finalPremium"))
                .orElse(null);
        if (value == null) {
            return "";
        }
        return currencyFormatter.format(value) + " " + currency;
    }

    private Optional<BigDecimal> decimal(JsonNode payload, String fieldName) {
        JsonNode node = payload.path(fieldName);
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(node.asText()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String text(JsonNode payload, String fieldName) {
        JsonNode node = payload.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
