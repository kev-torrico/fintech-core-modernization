package com.finbank.notifications.infrastructure;

import com.finbank.notifications.application.NotificationsService;
import com.finbank.notifications.domain.Notifications;
import com.finbank.notifications.domain.NotificationsType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationsServiceImpl implements NotificationsService {

    private final NotificationsRepository notificationsRepository;

    @Override
    @Transactional
    public void register(UUID eventId, UUID userId, String type, Map<String, String> payload) {
        if (eventId != null && notificationsRepository.existsByEventId(eventId)) {
            log.info("Event {} already processed; skipping duplicate notification", eventId);
            return;
        }

        Notifications notification = Notifications.builder()
            .eventId(eventId)
            .userId(userId)
            .type(NotificationsType.valueOf(type))
            .payload(payload)
            .build();

        try {
            notificationsRepository.save(notification);
        } catch (DataIntegrityViolationException ex) {
            // Condición de carrera: otra entrega concurrente del mismo evento ganó la
            // escritura primero (índice único sobre event_id). Tratarlo como éxito idempotente.
            log.info("Event {} was concurrently processed; ignoring duplicate", eventId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notifications> getForUser(UUID userId) {
        return notificationsRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
