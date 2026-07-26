package com.modularbank.modules.audit.api;

import com.modularbank.modules.audit.application.AuditService;
import com.modularbank.modules.audit.domain.AuditEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public List<AuditEntry> getAuditLog(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return auditService.getForUser(userId);  // returns only the calling user's own entries
    }
}
