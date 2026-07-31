package com.modularbank.modules.audit.infrastructure.kafka;

import com.modularbank.modules.audit.application.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Adaptador de entrada (Kafka) del módulo de Auditoría. ms-transfers es ahora el
 * dueño del caso de uso "ejecutar transferencia"; el Monolito solo se entera de que
 * ocurrió a través de este evento, y registra la entrada de auditoría igual que
 * antes hacía TransferUseCase de forma síncrona.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferAuditEventListener {

    private final AuditService auditService;

    @KafkaListener(topics = "${finbank.kafka.topics.transfers}", groupId = "finbank-monolith-audit")
    public void onTransferExecuted(TransferAuditEvent event) {
        log.info("Received transfer audit event {} for transfer {}", event.eventId(), event.transferId());
        auditService.record(event.userId(), "TRANSFER_EXECUTED", Map.of(
            "transferId", event.transferId().toString(),
            "amount", event.amount().toPlainString()
        ));
    }
}
