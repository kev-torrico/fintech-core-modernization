package com.modularbank.modules.audit.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Spring Boot detecta estos beans automáticamente y los inyecta en el
 * ConcurrentKafkaListenerContainerFactory que auto-configura a partir de
 * spring.kafka.consumer.* — no hace falta redeclarar ConsumerFactory ni la factory.
 *
 * Si TransferAuditEventListener#onTransferExecuted falla, el contenedor reintenta 3
 * veces en el propio hilo del listener (backoff 1s, 2s, 4s) antes de descartar el
 * mensaje y avanzar el offset. Complementa, no reemplaza, la idempotencia por eventId
 * de AuditServiceImpl.
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
                "Giving up on transfer audit event after 3 retries: partition={} offset={} key={}: {}",
                record.partition(), record.offset(), record.key(), exception.getMessage(), exception),
            backOff
        );
    }

    /**
     * En Spring Boot 3.2.x, `spring.kafka.listener.observation-enabled` se lee en
     * KafkaProperties pero la autoconfiguración NUNCA lo aplica a ContainerProperties
     * (gap corregido recién en Boot 3.3). Sin esto, el listener no busca `traceparent`
     * en los headers del record y Brave le abre una traza nueva ("Parent ID: none" en
     * Zipkin) en vez de anidarse bajo la traza HTTP que originó el mensaje en ms-transfers.
     *
     * Este bean SÍ es un punto de extensión soportado: KafkaAnnotationDrivenConfiguration
     * inyecta cualquier ContainerCustomizer<Object,Object,ConcurrentMessageListenerContainer<...>>
     * disponible en el contexto vía factory.setContainerCustomizer(...).
     */
    @Bean
    public ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>>
            observationContainerCustomizer() {
        return container -> container.getContainerProperties().setObservationEnabled(true);
    }
}
