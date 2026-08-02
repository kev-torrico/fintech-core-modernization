package com.finbank.gateway.filter.global;

import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
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
 * status code de la respuesta ya resuelta — en ese punto, si la ruta pasó por
 * AuthenticationFilter, el request ya trae X-User-Id.
 *
 * traceId/spanId NO se agregan a mano: Micrometer Tracing puebla el MDC
 * automáticamente y logstash-logback-encoder los serializa como campos JSON de primer
 * nivel (ver logback-spring.xml). userId sí se agrega explícitamente como argumento
 * estructurado porque es contexto de negocio propio del Gateway, no de la traza.
 */
@Component
@Slf4j
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long start = System.currentTimeMillis();

        log.info(">> {} {}", request.getMethod(), request.getURI(),
            StructuredArguments.kv("userId", currentUserId(request)));

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long durationMs = System.currentTimeMillis() - start;
            log.info("<< {} {} -> {} ({} ms)",
                request.getMethod(), request.getURI(),
                exchange.getResponse().getStatusCode(), durationMs,
                StructuredArguments.kv("userId", currentUserId(request)),
                StructuredArguments.kv("durationMs", durationMs));
        }));
    }

    private String currentUserId(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst("X-User-Id");
        return userId != null ? userId : "anonymous";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
