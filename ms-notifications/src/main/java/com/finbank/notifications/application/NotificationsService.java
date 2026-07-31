package com.finbank.notifications.application;

import com.finbank.notifications.domain.Notifications;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationsService {
    /**
     * Idempotente respecto a eventId: si ya existe una notificación con ese eventId,
     * no vuelve a insertarla (protege contra la redelivery del consumidor Kafka, tanto
     * por reintentos de DefaultErrorHandler como por rebalanceos/reconexiones).
     */
    void register(UUID eventId, UUID userId, String type, Map<String, String> payload);
    List<Notifications> getForUser(UUID userId);
}
