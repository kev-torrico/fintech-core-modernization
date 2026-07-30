package com.finbank.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Spring Security WebFlux se limita aquí a desactivar los mecanismos por defecto
     * (CSRF, login por formulario, HTTP Basic) y a dejar pasar todo el tráfico: la
     * decisión real de autenticar (validar JWT, exigir Bearer, devolver 401) la toma
     * {@code AuthenticationFilter}, un GlobalFilter de Spring Cloud Gateway que se
     * ejecuta más adelante en la cadena reactiva, dentro del enrutamiento del propio
     * Gateway. Esto le da control total para mutar la petición (inyectar X-User-Id,
     * X-User-Email, X-User-Roles) antes de reenviarla al servicio downstream, algo que
     * un AuthenticationManager de Spring Security no resuelve de forma tan directa.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
            .build();
    }
}
