package com.modularbank.modules.notifications.api;

import com.modularbank.modules.notifications.application.NotificationsService;
import com.modularbank.modules.notifications.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationsService notificationsService;

    @GetMapping
    public List<Notification> getNotifications(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return notificationsService.getForUser(userId);
    }
}
