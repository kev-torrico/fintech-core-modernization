package com.modularbank.modules.notifications.infrastructure;

import com.modularbank.modules.notifications.application.NotificationsService;
import com.modularbank.modules.notifications.domain.Notification;
import com.modularbank.modules.notifications.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationsServiceImpl implements NotificationsService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void send(UUID userId, NotificationType type, Map<String, String> payload) {
        Notification notification = Notification.builder()
            .userId(userId)
            .type(type)
            .payload(payload)
            .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getForUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
