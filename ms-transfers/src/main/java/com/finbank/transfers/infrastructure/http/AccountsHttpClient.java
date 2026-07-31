package com.finbank.transfers.infrastructure.http;

import com.finbank.transfers.application.dto.AccountSummary;
import com.finbank.transfers.application.port.out.AccountsPort;
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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountsHttpClient implements AccountsPort {

    private final WebClient gatewayWebClient;

    @Override
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
        } catch (WebClientResponseException ex) {
            log.error("Accounts validation failed with status {}: {}", ex.getStatusCode(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Accounts service returned an unexpected error");
        } catch (Exception ex) {
            log.error("Accounts service unreachable via gateway", ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Accounts service is unavailable");
        }
    }

    @Override
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
        } catch (WebClientResponseException ex) {
            log.error("Account existence check failed with status {}: {}", ex.getStatusCode(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Accounts service returned an unexpected error");
        } catch (Exception ex) {
            log.error("Accounts service unreachable via gateway", ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Accounts service is unavailable");
        }
    }
}
