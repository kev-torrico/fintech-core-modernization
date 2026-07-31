package com.finbank.notifications.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Spring Boot detecta este bean automáticamente y lo inyecta en el
 * ConcurrentKafkaListenerContainerFactory que auto-configura a partir de
 * spring.kafka.consumer.* — no hace falta redeclarar ConsumerFactory ni la factory.
 *
 * Si @KafkaListener#onNotificationEvent falla, el contenedor reintenta 3 veces en el
 * propio hilo del listener (backoff 1s, 2s, 4s) antes de descartar el mensaje y avanzar
 * el offset. Complementa, no reemplaza, la idempotencia por eventId de NotificationsServiceImpl.
 */
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);
        backOff.setMaxAttempts(3); // 3 reintentos (1s, 2s, 4s) además del intento inicial

        return new DefaultErrorHandler(
            (record, exception) -> log.error(
                "Giving up on notification event after 3 retries: partition={} offset={} key={}: {}",
                record.partition(), record.offset(), record.key(), exception.getMessage(), exception),
            backOff
        );
    }
}
