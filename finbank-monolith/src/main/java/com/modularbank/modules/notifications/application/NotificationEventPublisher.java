package com.modularbank.modules.notifications.application;

import com.modularbank.modules.notifications.domain.NotificationType;
import java.util.Map;
import java.util.UUID;

public interface NotificationEventPublisher {
    void publish(UUID userId, NotificationType type, Map<String, String> payload);
}
