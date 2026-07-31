package com.finbank.transfers.application;

import com.finbank.transfers.application.dto.AccountSummary;
import com.finbank.transfers.application.dto.TransfersRequest;
import com.finbank.transfers.application.port.out.AccountsPort;
import com.finbank.transfers.application.port.out.TransferEventPublisher;
import com.finbank.transfers.domain.TransferStatus;
import com.finbank.transfers.domain.Transfers;
import com.finbank.transfers.infrastructure.TransfersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso "ejecutar transferencia", extraído del Monolito. postgres-transfers está
 * aislada, así que la validación de cuentas (existencia, titularidad, saldo) no es una
 * consulta local: es una llamada HTTP de vuelta al api-gateway (ver AccountsPort), que
 * revalida el mismo Bearer token del usuario.
 *
 * Nota de alcance: este servicio NO debita/acredita saldos — Cuentas todavía vive en el
 * Monolito y no expone (todavía) un contrato de mutación entre servicios. Lo que aquí se
 * registra es el movimiento en el ledger de Transferencias y los eventos de integración;
 * mover el saldo real requiere extraer también el módulo de Cuentas o exponer un
 * endpoint interno de débito/crédito, lo cual queda fuera del alcance de este incremento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransfersUseCase {

    private final TransfersRepository transfersRepository;
    private final AccountsPort accountsPort;
    private final TransferEventPublisher transferEventPublisher;

    @Transactional
    public Transfers execute(UUID userId, String bearerToken, String idempotencyKey, TransfersRequest request) {
        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();
        if (hasIdempotencyKey) {
            Optional<Transfers> existing = transfersRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotency key {} already processed as transfer {}; returning stored result",
                    idempotencyKey, existing.get().getId());
                return existing.get();
            }
        }

        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Source and target accounts must be different");
        }

        List<AccountSummary> owned = accountsPort.findOwnedAccounts(bearerToken);
        AccountSummary source = owned.stream()
            .filter(account -> account.id().equals(request.sourceAccountId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Source account does not belong to the authenticated user"));

        boolean targetExists = accountsPort.accountExists(bearerToken, request.targetAccountId());
        if (!targetExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target account not found");
        }

        if (source.balance().compareTo(request.amount()) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        }

        Transfers transfer = Transfers.builder()
            .sourceAccountId(request.sourceAccountId())
            .targetAccountId(request.targetAccountId())
            .amount(request.amount())
            .reference(request.reference())
            .idempotencyKey(hasIdempotencyKey ? idempotencyKey : null)
            .status(TransferStatus.COMPLETED)
            .build();

        try {
            transfer = transfersRepository.save(transfer);
        } catch (DataIntegrityViolationException ex) {
            // Condición de carrera: otra petición concurrente con la misma X-Idempotency-Key
            // ganó la escritura primero (índice único). No es un error: es el mismo caso que
            // el chequeo de arriba, solo que perdimos la carrera por microsegundos.
            if (hasIdempotencyKey) {
                return transfersRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
            }
            throw ex;
        }

        transferEventPublisher.publishTransferExecuted(userId, transfer);

        return transfer;
    }

    @Transactional(readOnly = true)
    public List<Transfers> getHistory(UUID userId, String bearerToken, UUID accountId) {
        List<AccountSummary> owned = accountsPort.findOwnedAccounts(bearerToken);
        boolean owns = owned.stream().anyMatch(account -> account.id().equals(accountId));
        if (!owns) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Account does not belong to the authenticated user");
        }
        return transfersRepository
            .findBySourceAccountIdOrTargetAccountIdOrderByCreatedAtDesc(accountId, accountId);
    }
}
