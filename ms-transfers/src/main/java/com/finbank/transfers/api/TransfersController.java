package com.finbank.transfers.api;

import com.finbank.transfers.application.TransfersUseCase;
import com.finbank.transfers.application.dto.TransfersRequest;
import com.finbank.transfers.domain.Transfers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

    private final TransfersUseCase transfersUseCase;

    @PostMapping
    public ResponseEntity<?> executeTransfer(@RequestBody @Valid TransfersRequest request,
                                             Authentication auth,
                                             @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        UUID userId = (UUID) auth.getPrincipal();
        try {
            Transfers transfer = transfersUseCase.execute(userId, authorization, request);
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
    public ResponseEntity<?> getHistory(@RequestParam UUID accountId,
                                        Authentication auth,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        UUID userId = (UUID) auth.getPrincipal();
        try {
            List<Transfers> history = transfersUseCase.getHistory(userId, authorization, accountId);
            return ResponseEntity.ok(history);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Error"));
        }
    }
}
