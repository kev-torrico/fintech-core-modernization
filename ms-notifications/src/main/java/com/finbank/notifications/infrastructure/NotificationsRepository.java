package com.finbank.notifications.infrastructure;

import com.finbank.notifications.domain.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationsRepository extends JpaRepository<Notifications, UUID> {
    List<Notifications> findByUserIdOrderByCreatedAtDesc(String userId);
}
