package com.finbank.transfers.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * La dependencia de validación de cuentas (Monolito, vía api-gateway) no responde de
 * forma fiable: se agotaron los reintentos o el circuito está abierto. Se modela como
 * un 503 — la transferencia ni siquiera pudo evaluarse, no es un rechazo de negocio.
 */
public class AccountServiceUnavailableException extends ResponseStatusException {

    public AccountServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
