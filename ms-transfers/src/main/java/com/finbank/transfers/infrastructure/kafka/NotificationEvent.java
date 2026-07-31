package com.finbank.transfers.infrastructure.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publicado en el tópico notification-events con la misma forma que espera el
 * consumidor de ms-notifications (com.finbank.notifications.infrastructure.kafka.NotificationEvent).
 */
public record NotificationEvent(
    UUID eventId,
    UUID userId,
    String type,
    Map<String, String> payload,
    Instant occurredAt
) {
}
