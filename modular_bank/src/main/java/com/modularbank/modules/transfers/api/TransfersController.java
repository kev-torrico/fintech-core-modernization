package com.modularbank.modules.transfers.api;

import com.modularbank.modules.accounts.application.AccountsService;
import com.modularbank.modules.accounts.application.dto.AccountSummary;
import com.modularbank.modules.transfers.application.TransferUseCase;
import com.modularbank.modules.transfers.application.dto.TransferRequest;
import com.modularbank.modules.transfers.domain.Transfer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransfersController {

    private final TransferUseCase transferUseCase;
    private final AccountsService accountsService;

    @PostMapping
    public ResponseEntity<?> executeTransfer(@RequestBody @Valid TransferRequest request,
                                             Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        try {
            Transfer transfer = transferUseCase.execute(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Error"));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Transfer>> getHistory(
            @RequestParam UUID accountId,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<AccountSummary> owned = accountsService.findByOwner(userId);
        boolean owns = owned.stream().anyMatch(a -> a.id().equals(accountId));
        if (!owns) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(transferUseCase.getHistory(accountId));
    }
}
