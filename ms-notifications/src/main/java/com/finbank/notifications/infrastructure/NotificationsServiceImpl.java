package com.finbank.notifications.infrastructure;

import com.finbank.notifications.application.NotificationsService;
import com.finbank.notifications.domain.Notifications;
import com.finbank.notifications.domain.NotificationsType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationsServiceImpl implements NotificationsService {

    private final NotificationsRepository notificationsRepository;

    @Override
    @Transactional
    public void register(UUID userId, String type, Map<String, String> payload) {
        Notifications notification = Notifications.builder()
            .userId(userId.toString())
            .type(NotificationsType.valueOf(type))
            .payload(payload)
            .build();
        notificationsRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notifications> getForUser(UUID userId) {
        return notificationsRepository.findByUserIdOrderByCreatedAtDesc(userId.toString());
    }
}
