package com.modularbank.modules.audit.application;

import com.modularbank.modules.audit.domain.AuditEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuditService {
    void record(UUID userId, String action, Map<String, String> metadata);

    /**
     * Variante idempotente respecto a eventId, para registros disparados por un evento
     * Kafka: si ya existe una entrada con ese eventId, no vuelve a insertarla (protege
     * contra la redelivery del consumidor, tanto por reintentos como por rebalanceos).
     */
    void record(UUID eventId, UUID userId, String action, Map<String, String> metadata);

    List<AuditEntry> getForUser(UUID userId);
}
