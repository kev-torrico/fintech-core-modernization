package com.modularbank.modules.audit.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrato del evento de integración publicado por ms-transfers en el tópico
 * transfer-events. Se mantiene como una clase propia del módulo de Auditoría del
 * Monolito (no se comparte el .jar con ms-transfers) para no acoplar ambos
 * despliegues a un mismo modelo — solo debe coincidir la forma del JSON.
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
