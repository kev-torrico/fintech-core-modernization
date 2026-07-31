package com.finbank.transfers.infrastructure.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient gatewayWebClient(@Value("${finbank.gateway.uri}") String gatewayUri) {
        return WebClient.builder()
            .baseUrl(gatewayUri)
            .build();
    }
}
