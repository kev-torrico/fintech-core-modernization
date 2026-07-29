package com.modularbank.modules.audit.application;

import com.modularbank.modules.audit.domain.AuditEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuditService {
    void record(UUID userId, String action, Map<String, String> metadata);
    List<AuditEntry> getForUser(UUID userId);
}
