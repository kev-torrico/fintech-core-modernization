package com.finbank.transfers.application.port.out;

import com.finbank.transfers.application.dto.AccountSummary;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida hacia el dominio de Cuentas, que sigue viviendo en el Monolito.
 * Como postgres-transfers está aislada (Database-per-Service), ms-transfers no puede
 * leer ni escribir directamente las cuentas: debe validar por HTTP, a través del
 * api-gateway, reenviando el Bearer token del usuario autenticado.
 */
public interface AccountsPort {

    /**
     * Cuentas que posee el usuario dueño del token (GET /api/v1/accounts).
     */
    List<AccountSummary> findOwnedAccounts(String bearerToken);

    /**
     * Existencia de una cuenta, sin importar el propietario (GET /api/v1/accounts/{id}/exists).
     */
    boolean accountExists(String bearerToken, UUID accountId);
}
