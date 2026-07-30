package com.modularbank.modules.notifications.infrastructure;

import com.modularbank.modules.notifications.application.NotificationEventPublisher;
import com.modularbank.modules.notifications.application.event.NotificationEvent;
import com.modularbank.modules.notifications.domain.NotificationType;
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
public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Value("${finbank.kafka.topics.notifications}")
    private String notificationsTopic;

    @Override
    public void publish(UUID userId, NotificationType type, Map<String, String> payload) {
        NotificationEvent event = new NotificationEvent(
            UUID.randomUUID(),
            userId,
            type.name(),
            payload,
            Instant.now()
        );
        kafkaTemplate.send(notificationsTopic, userId.toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish notification event {} for user {}", event.eventId(), userId, ex);
                }
            });
    }
}
