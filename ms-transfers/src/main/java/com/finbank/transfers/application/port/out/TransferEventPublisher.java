package com.finbank.transfers.application.port.out;

import com.finbank.transfers.domain.Transfers;
import java.util.UUID;

/**
 * Puerto de salida hacia Kafka. Tras registrar una transferencia, publica los eventos
 * de integración que consumen el módulo de Auditoría del Monolito (transfer-events) y
 * ms-notifications (notification-events).
 */
public interface TransferEventPublisher {
    void publishTransferExecuted(UUID userId, Transfers transfer);
}
