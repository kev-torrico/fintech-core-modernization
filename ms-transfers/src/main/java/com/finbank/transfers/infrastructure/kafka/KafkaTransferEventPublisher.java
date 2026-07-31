package com.finbank.transfers.infrastructure.kafka;

import com.finbank.transfers.application.port.out.TransferEventPublisher;
import com.finbank.transfers.domain.Transfers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaTransferEventPublisher implements TransferEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${finbank.kafka.topics.transfers}")
    private String transfersTopic;

    @Value("${finbank.kafka.topics.notifications}")
    private String notificationsTopic;

    @Override
    public void publishTransferExecuted(UUID userId, Transfers transfer) {
        Instant now = Instant.now();

        TransferAuditEvent auditEvent = new TransferAuditEvent(
            UUID.randomUUID(),
            transfer.getId(),
            userId,
            transfer.getSourceAccountId(),
            transfer.getTargetAccountId(),
            transfer.getAmount(),
            transfer.getReference(),
            now
        );
        send(transfersTopic, transfer.getId().toString(), auditEvent);

        NotificationEvent notificationEvent = new NotificationEvent(
            UUID.randomUUID(),
            userId,
            "TRANSFER_SENT",
            Map.of(
                "amount", transfer.getAmount().toPlainString(),
                "targetAccountId", transfer.getTargetAccountId().toString()
            ),
            now
        );
        send(notificationsTopic, userId.toString(), notificationEvent);
    }

    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage(), ex);
            }
        });
    }
}
