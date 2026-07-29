package com.modularbank.modules.audit.infrastructure;

import com.modularbank.modules.audit.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
    List<AuditEntry> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
