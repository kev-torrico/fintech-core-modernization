package com.finbank.gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.Map;

/**
 * Respuestas de fallback cuando un servicio downstream no está disponible. No hay
 * todavía un Circuit Breaker (Resilience4j) delante de las rutas — es un punto de
 * extensión: al añadirlo, cada ruta en application.yml apuntará su filtro
 * CircuitBreaker a uno de estos endpoints en lugar de dejar que el error se propague.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/monolith")
    public ResponseEntity<Map<String, Object>> monolithFallback() {
        return unavailable("El servicio Core/Auth (Monolito) no está disponible en este momento");
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> notificationsFallback() {
        return unavailable("El servicio de Notificaciones no está disponible en este momento");
    }

    private ResponseEntity<Map<String, Object>> unavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
            "error", "Service Unavailable",
            "message", message,
            "timestamp", Instant.now().toString()
        ));
    }
}
