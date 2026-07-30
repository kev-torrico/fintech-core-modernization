package com.finbank.gateway.filter.custom;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * GatewayFilterFactory de propósito general para enriquecer la petición saliente hacia
 * los servicios downstream, independiente de la autenticación (que resuelve
 * {@link AuthenticationFilter}). Punto de extensión pensado para la fase de
 * observabilidad: aquí se generará/propagará X-Trace-Id y X-Correlation-Id
 * (Micrometer Tracing / OpenTelemetry) una vez se integren esos patrones.
 */
@Component
public class RequestHeaderFilter extends AbstractGatewayFilterFactory<RequestHeaderFilter.Config> {

    public RequestHeaderFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Gateway-Source", "finbank-api-gateway")
                // TODO (observabilidad/trazabilidad — fase futura): X-Trace-Id / X-Correlation-Id
                .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    public static class Config {
    }
}
