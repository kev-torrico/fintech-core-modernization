package com.finbank.transfers.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Publicado en el tópico transfer-events. Lo consume el módulo de Auditoría del
 * Monolito (ver TransferAuditEventListener) para registrar el log correspondiente.
 */
public record TransferAuditEvent(
    UUID eventId,
    UUID transferId,
    UUID userId,
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount,
    String reference,
    Instant occurredAt
) {
}
