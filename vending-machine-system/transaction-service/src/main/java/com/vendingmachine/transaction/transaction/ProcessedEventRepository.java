package com.vendingmachine.transaction.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByEventIdAndEventType(String eventId, String eventType);

    Optional<ProcessedEvent> findByEventId(String eventId);
}