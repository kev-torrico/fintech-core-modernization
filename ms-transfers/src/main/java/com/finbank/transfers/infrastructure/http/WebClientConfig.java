package com.finbank.transfers.infrastructure.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Se inyecta el {@link WebClient.Builder} que Spring Boot auto-configura (no
     * {@code WebClient.builder()} "a pelo"): ese builder ya trae registrado el
     * {@code ObservationRegistry} de Micrometer, que es lo que instrumenta la llamada
     * saliente con un span cliente y propaga el header `traceparent` hacia el
     * api-gateway. Con un WebClient construido "a mano" no hay traza que propagar.
     */
    @Bean
    public WebClient gatewayWebClient(WebClient.Builder webClientBuilder,
                                      @Value("${finbank.gateway.uri}") String gatewayUri) {
        return webClientBuilder
            .baseUrl(gatewayUri)
            .build();
    }
}
