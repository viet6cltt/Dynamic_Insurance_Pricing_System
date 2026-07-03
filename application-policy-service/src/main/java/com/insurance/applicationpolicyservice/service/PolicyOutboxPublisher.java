package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.applicationpolicyservice.model.OutboxEvent;
import com.insurance.applicationpolicyservice.model.OutboxStatus;
import com.insurance.applicationpolicyservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );
        for (OutboxEvent event : events) {
            publishOne(event);
        }
        return events.size();
    }

    private void publishOne(OutboxEvent event) {
        event.setLockedAt(Instant.now());
        try {
            String key = resolveKafkaKey(event);
            String message = objectMapper.writeValueAsString(event.getPayload());
            kafkaTemplate.send(event.getTopicName(), key, message).get(10, TimeUnit.SECONDS);
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
            log.info("Published policy outbox event {} to topic {}", event.getEventId(), event.getTopicName());
        } catch (Exception ex) {
            int nextRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(nextRetryCount);
            event.setLastError(ex.getMessage());
            event.setStatus(nextRetryCount >= maxRetries ? OutboxStatus.FAILED : OutboxStatus.PENDING);
            log.warn("Failed to publish policy outbox event {} retry {}/{}: {}",
                    event.getEventId(), nextRetryCount, maxRetries, ex.getMessage());
        }
    }

    private String resolveKafkaKey(OutboxEvent event) {
        JsonNode contractIdNode = event.getPayload().path("payload").path("contractId");
        if (!contractIdNode.isMissingNode() && !contractIdNode.asText().isBlank()) {
            return contractIdNode.asText();
        }
        return event.getAggregateId().toString();
    }
}
