package com.modularbank.modules.notifications.application.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationEvent(
    UUID eventId,
    UUID userId,
    String type,
    Map<String, String> payload,
    Instant occurredAt
) {
}
