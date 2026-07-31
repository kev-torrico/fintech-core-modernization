package com.finbank.transfers.infrastructure.http;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.io.IOException;
import java.util.function.Predicate;

/**
 * Decide qué fallos de AccountsHttpClient son "transitorios" (vale la pena reintentar
 * y contarlos contra el Circuit Breaker) frente a rechazos de negocio deliberados
 * (401 por token inválido) que deben propagarse de inmediato, sin reintentos ni
 * impacto en el estado del circuito.
 *
 * Referenciado desde application.yml: resilience4j.retry.instances.accountsService.retry-exception-predicate
 */
public class TransientHttpErrorPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientEx) {
            return webClientEx.getStatusCode().is5xxServerError();
        }
        // Conexión rechazada, timeout de transporte, DNS, etc.: nunca llegó a haber respuesta.
        if (throwable instanceof WebClientRequestException) {
            return true;
        }
        return throwable instanceof IOException;
    }
}
