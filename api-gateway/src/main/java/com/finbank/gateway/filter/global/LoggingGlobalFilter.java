package com.finbank.gateway.filter.global;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logging de entrada/salida de cada petición enrutada por el Gateway. Se ejecuta al
 * final de la cadena de GlobalFilters (LOWEST_PRECEDENCE) para poder reportar el
 * status code de la respuesta ya resuelta.
 *
 * TODO (observabilidad/trazabilidad — fase futura): incluir X-Trace-Id / X-Correlation-Id
 * en el MDC de logging para poder correlacionar logs entre el Gateway y los servicios downstream.
 */
@Component
@Slf4j
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long start = System.currentTimeMillis();
        log.info(">> {} {}", request.getMethod(), request.getURI());

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long durationMs = System.currentTimeMillis() - start;
            log.info("<< {} {} -> {} ({} ms)",
                request.getMethod(), request.getURI(),
                exchange.getResponse().getStatusCode(), durationMs);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
