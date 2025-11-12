package com.vendingmachine.dispensing.util;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    Optional<ProcessedEvent> findByEventIdAndEventType(String eventId, String eventType);

    boolean existsByEventIdAndEventType(String eventId, String eventType);
}