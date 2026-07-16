package com.miniweverse.membership.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipOutboxEventRepository extends JpaRepository<MembershipOutboxEvent, Long> {

    List<MembershipOutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable);
}
