package com.danzzan.domain.ticket.repository;

import com.danzzan.domain.ticket.model.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
