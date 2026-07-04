package com.insurance.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.paymentservice.dto.CreateMockPaymentRequest;
import com.insurance.paymentservice.model.OutboxEvent;
import com.insurance.paymentservice.model.OutboxStatus;
import com.insurance.paymentservice.model.Payment;
import com.insurance.paymentservice.model.PaymentStatus;
import com.insurance.paymentservice.repository.OutboxEventRepository;
import com.insurance.paymentservice.repository.PaymentRepository;
import com.insurance.paymentservice.repository.PaymentTransactionLogRepository;
import com.insurance.paymentservice.service.OutboxPublisher;
import com.insurance.paymentservice.service.PaymentExpirationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentTransactionLogRepository paymentTransactionLogRepository;

    @Autowired
    private PaymentExpirationService paymentExpirationService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        paymentTransactionLogRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void createMockPaymentSuccess() throws Exception {
        CreateMockPaymentRequest request = paymentRequest("SUCCESS");

        mockMvc.perform(post("/payments/mock")
                        .header("X-USER-ID", "SYSTEM")
                        .header("X-USER-ROLE", "SYSTEM")
                        .header("Idempotency-Key", "contract-" + request.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.contractId").value(request.contractId().toString()));

        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> request.contractId().equals(p.getContractId()))
                .findFirst()
                .orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        assertEquals(1, outboxEventRepository.findByEventType("payment.succeeded").size());
    }

    @Test
    void createMockPaymentFailedAndPending() throws Exception {
        mockMvc.perform(post("/payments/mock")
                        .header("X-USER-ID", "SYSTEM")
                        .header("X-USER-ROLE", "SYSTEM")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest("FAILED"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(post("/payments/mock")
                        .header("X-USER-ID", "SYSTEM")
                        .header("X-USER-ROLE", "SYSTEM")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest("PENDING"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertFalse(outboxEventRepository.findByEventType("payment.failed").isEmpty());
        assertFalse(outboxEventRepository.findByEventType("payment.created").isEmpty());
    }

    @Test
    void idempotencyKeyReturnsExistingPayment() throws Exception {
        CreateMockPaymentRequest request = paymentRequest("SUCCESS");
        String idempotencyKey = "contract-" + request.contractId();

        String firstResponse = mockMvc.perform(post("/payments/mock")
                        .header("X-USER-ID", "SYSTEM")
                        .header("X-USER-ROLE", "SYSTEM")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/payments/mock")
                        .header("X-USER-ID", "SYSTEM")
                        .header("X-USER-ROLE", "SYSTEM")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID firstPaymentId = UUID.fromString(objectMapper.readTree(firstResponse).get("paymentId").asText());
        UUID secondPaymentId = UUID.fromString(objectMapper.readTree(secondResponse).get("paymentId").asText());
        assertEquals(firstPaymentId, secondPaymentId);
        assertEquals(1, paymentRepository.findAll().stream()
                .filter(p -> request.contractId().equals(p.getContractId()))
                .count());
    }

    @Test
    void expirePendingPaymentCreatesExpiredEvent() {
        Payment payment = Payment.builder()
                .contractId(UUID.randomUUID())
                .quoteId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(120))
                .currency("VND")
                .paymentMethod("MOCK")
                .provider("MOCK_PROVIDER")
                .status(PaymentStatus.PENDING)
                .idempotencyKey(UUID.randomUUID().toString())
                .externalTransactionId("mock-" + UUID.randomUUID())
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        Payment saved = paymentRepository.save(payment);

        int expiredCount = paymentExpirationService.expirePendingPayments();

        Payment expired = paymentRepository.findById(saved.getPaymentId()).orElseThrow();
        assertTrue(expiredCount >= 1);
        assertEquals(PaymentStatus.EXPIRED, expired.getStatus());
        assertFalse(outboxEventRepository.findByEventType("payment.expired").isEmpty());
    }

    @Test
    void outboxPublisherPublishesPendingEvents() throws Exception {
        Mockito.when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        CreateMockPaymentRequest request = paymentRequest("SUCCESS");
        mockMvc.perform(post("/payments/mock")
                        .header("X-USER-ID", "SYSTEM")
                        .header("X-USER-ROLE", "SYSTEM")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        int publishedCount = outboxPublisher.publishPendingEvents();

        assertTrue(publishedCount >= 1);
        Mockito.verify(kafkaTemplate, Mockito.atLeastOnce())
                .send(ArgumentMatchers.eq("payment.events"), anyString(), anyString());
        assertTrue(outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType().equals("payment.succeeded"))
                .map(OutboxEvent::getStatus)
                .anyMatch(OutboxStatus.PUBLISHED::equals));
    }

    private CreateMockPaymentRequest paymentRequest(String simulateResult) {
        return new CreateMockPaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.valueOf(120_000),
                "VND",
                "MOCK",
                simulateResult
        );
    }
}
