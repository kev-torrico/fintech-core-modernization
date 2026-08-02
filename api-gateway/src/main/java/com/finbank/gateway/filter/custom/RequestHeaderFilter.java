package com.finbank.gateway.filter.custom;

import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * GatewayFilterFactory de propósito general para enriquecer la petición saliente hacia
 * los servicios downstream, independiente de la autenticación (que resuelve
 * {@link AuthenticationFilter}).
 *
 * - X-Trace-Id: el traceId real de Micrometer Tracing. El estándar W3C (`traceparent`)
 *   ya viaja en las cabeceras HTTP y es lo que efectivamente propaga la traza; este
 *   header es un extra de conveniencia para humanos/herramientas que no parsean
 *   `traceparent` (grep en logs, soporte, curl manual).
 * - X-Correlation-Id: identificador de negocio, independiente del backend de trazas.
 *   Si el cliente ya lo envió, se respeta (permite correlacionar un ticket de soporte
 *   con la petición original); si no, se genera uno nuevo por request.
 */
@Component
public class RequestHeaderFilter extends AbstractGatewayFilterFactory<RequestHeaderFilter.Config> {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    public RequestHeaderFilter(Tracer tracer) {
        super(Config.class);
        this.tracer = tracer;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest incoming = exchange.getRequest();

            String correlationId = incoming.getHeaders().getFirst(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : "";

            ServerHttpRequest mutatedRequest = incoming.mutate()
                .header("X-Gateway-Source", "finbank-api-gateway")
                .header(CORRELATION_ID_HEADER, correlationId)
                .header(TRACE_ID_HEADER, traceId)
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    public static class Config {
    }
}
