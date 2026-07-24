package com.miniweverse.common.notification.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxEventRepository extends JpaRepository<NotificationOutboxEvent, Long> {

    List<NotificationOutboxEvent> findByStatusOrderByIdAsc(NotificationOutboxStatus status, Pageable pageable);
}
