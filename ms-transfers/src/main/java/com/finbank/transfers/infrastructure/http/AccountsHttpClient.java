package com.finbank.transfers.infrastructure.http;

import com.finbank.transfers.application.dto.AccountSummary;
import com.finbank.transfers.application.port.out.AccountsPort;
import com.finbank.transfers.domain.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de salida HTTP. Revalida la identidad reenviando el mismo Bearer token que
 * llegó a ms-transfers: la petición vuelve a pasar por el AuthenticationFilter del
 * api-gateway, así que las cuentas devueltas ya están filtradas por el usuario dueño
 * del token — no hace falta (ni se puede) especificar un userId aparte.
 *
 * Resiliencia: un 401 es un rechazo de negocio (token inválido) y se traduce y se
 * relanza de inmediato, sin pasar por Retry/CircuitBreaker (ver resilience4j.*.ignore-exceptions
 * en application.yml). Cualquier otro fallo (5xx, timeout, conexión rechazada) se deja
 * propagar tal cual para que @Retry lo reintente con backoff exponencial y, si el fallo
 * persiste, @CircuitBreaker lo cuente contra el umbral y eventualmente abra el circuito,
 * derivando al método de fallback.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountsHttpClient implements AccountsPort {

    private static final String ACCOUNTS_SERVICE = "accountsService";

    private final WebClient gatewayWebClient;

    @Override
    @CircuitBreaker(name = ACCOUNTS_SERVICE, fallbackMethod = "fallbackFindOwnedAccounts")
    @Retry(name = ACCOUNTS_SERVICE)
    public List<AccountSummary> findOwnedAccounts(String bearerToken) {
        try {
            AccountSummary[] accounts = gatewayWebClient.get()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve()
                .bodyToMono(AccountSummary[].class)
                .block();
            return accounts != null ? List.of(accounts) : List.of();
        } catch (WebClientResponseException.Unauthorized ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
        // Cualquier otra excepción (5xx, WebClientRequestException, timeout) se propaga
        // sin capturar: es responsabilidad de @Retry / @CircuitBreaker, no de este método.
    }

    @Override
    @CircuitBreaker(name = ACCOUNTS_SERVICE, fallbackMethod = "fallbackAccountExists")
    @Retry(name = ACCOUNTS_SERVICE)
    @SuppressWarnings("unchecked")
    public boolean accountExists(String bearerToken, UUID accountId) {
        try {
            Map<String, Boolean> response = gatewayWebClient.get()
                .uri("/api/v1/accounts/{id}/exists", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            return response != null && Boolean.TRUE.equals(response.get("exists"));
        } catch (WebClientResponseException.Unauthorized ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    // ---------------------------------------------------------------------
    // Fallbacks: firma = (parámetros originales..., Throwable). Se invocan cuando el
    // circuito está OPEN o cuando @Retry agota los 3 reintentos sin éxito.
    // ---------------------------------------------------------------------

    private List<AccountSummary> fallbackFindOwnedAccounts(String bearerToken, Throwable throwable) {
        log.error("Accounts validation unavailable (findOwnedAccounts) after retries/circuit-open: {}",
            throwable.getMessage(), throwable);
        throw new AccountServiceUnavailableException(
            "Accounts validation service is currently unavailable", throwable);
    }

    private boolean fallbackAccountExists(String bearerToken, UUID accountId, Throwable throwable) {
        log.error("Accounts validation unavailable (accountExists) after retries/circuit-open: {}",
            throwable.getMessage(), throwable);
        throw new AccountServiceUnavailableException(
            "Accounts validation service is currently unavailable", throwable);
    }
}
