package com.finbank.notifications.infrastructure.kafka;

import com.finbank.notifications.application.NotificationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationsService notificationsService;

    @KafkaListener(topics = "${finbank.kafka.topics.notifications}", groupId = "${spring.kafka.consumer.group-id}")
    public void onNotificationEvent(NotificationEvent event) {
        log.info("Received notification event {} for user {} (type={})", event.eventId(), event.userId(), event.type());
        notificationsService.register(event.eventId(), event.userId(), event.type(), event.payload());
    }
}
