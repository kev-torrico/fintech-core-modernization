package com.finbank.notifications.application;

import com.finbank.notifications.domain.Notifications;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationsService {
    void register(UUID userId, String type, Map<String, String> payload);
    List<Notifications> getForUser(UUID userId);
}
