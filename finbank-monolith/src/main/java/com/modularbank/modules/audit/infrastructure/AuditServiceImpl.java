package com.modularbank.modules.audit.infrastructure;

import com.modularbank.modules.audit.application.AuditService;
import com.modularbank.modules.audit.domain.AuditEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;

    @Override
    @Transactional
    public void record(UUID userId, String action, Map<String, String> metadata) {
        save(null, userId, action, metadata);
    }

    @Override
    @Transactional
    public void record(UUID eventId, UUID userId, String action, Map<String, String> metadata) {
        if (eventId != null && auditRepository.existsByEventId(eventId)) {
            log.info("Event {} already audited; skipping duplicate entry", eventId);
            return;
        }
        save(eventId, userId, action, metadata);
    }

    private void save(UUID eventId, UUID userId, String action, Map<String, String> metadata) {
        if (action == null || action.isBlank() || action.length() > 100) {
            throw new IllegalArgumentException("action must be 1-100 characters");
        }
        AuditEntry entry = AuditEntry.builder()
            .eventId(eventId)
            .userId(userId)
            .action(action)
            .metadata(metadata != null ? metadata : Map.of())
            .build();
        try {
            auditRepository.save(entry);
        } catch (DataIntegrityViolationException ex) {
            if (eventId == null) {
                throw ex;
            }
            // Condición de carrera: otra entrega concurrente del mismo evento ganó la
            // escritura primero (índice único sobre event_id). Éxito idempotente.
            log.info("Event {} was concurrently audited; ignoring duplicate", eventId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> getForUser(UUID userId) {
        return auditRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
