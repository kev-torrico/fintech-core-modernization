package com.finbank.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Traduce fallos de enrutamiento (servicio downstream caído, timeout, DNS no resuelto)
 * a una respuesta JSON consistente, en lugar del stacktrace HTML por defecto de WebFlux.
 * Se registra con precedencia alta (-2) para interceptar antes que el
 * DefaultErrorWebExceptionHandler de Spring Boot.
 */
@Component
@Order(-2)
@Slf4j
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        log.error("Gateway error handling {} {}: {}",
            exchange.getRequest().getMethod(), exchange.getRequest().getURI(), ex.getMessage());

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Unexpected gateway error");
        body.put("path", exchange.getRequest().getURI().getPath());
        body.put("timestamp", Instant.now().toString());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            bytes = ("{\"status\":500,\"error\":\"Internal Server Error\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ConnectException || ex instanceof TimeoutException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof ResponseStatusExceptionAdapter adapter) {
            return adapter.status();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Punto de extensión: si en el futuro se lanzan excepciones propias del Gateway
     * que ya traen un HttpStatus asociado, pueden implementar esta interfaz para que
     * resolveStatus las respete en lugar de caer siempre a 500/503.
     */
    public interface ResponseStatusExceptionAdapter {
        HttpStatus status();
    }
}
