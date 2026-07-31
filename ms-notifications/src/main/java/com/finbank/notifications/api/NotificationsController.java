package com.finbank.notifications.api;

import com.finbank.notifications.application.NotificationsService;
import com.finbank.notifications.domain.Notifications;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationsService notificationsService;

    @GetMapping
    public List<Notifications> getNotifications(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return notificationsService.getForUser(userId);
    }
}
