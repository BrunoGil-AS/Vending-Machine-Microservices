package com.vendingmachine.dispensing.repository;

import com.vendingmachine.dispensing.entity.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {

    Optional<FailedEvent> findByEventId(String eventId);

    @Query("SELECT f FROM FailedEvent f WHERE f.status = 'FAILED' AND f.retryCount < :maxRetryCount")
    List<FailedEvent> findRetryableEvents(@Param("maxRetryCount") int maxRetryCount);

    @Query("SELECT f FROM FailedEvent f WHERE f.status = 'FAILED' AND f.failedAt < :cutoffTime")
    List<FailedEvent> findStuckEvents(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT f FROM FailedEvent f WHERE f.originalTopic = :topic AND f.status = 'FAILED'")
    List<FailedEvent> findFailedEventsByTopic(@Param("topic") String topic);

    @Query("SELECT COUNT(f) FROM FailedEvent f WHERE f.status = 'FAILED'")
    long countFailedEvents();

    @Query("SELECT f FROM FailedEvent f WHERE f.status = 'FAILED' ORDER BY f.failedAt DESC")
    List<FailedEvent> findRecentFailedEvents();

    @Query("SELECT DISTINCT f.errorType FROM FailedEvent f WHERE f.failedAt > :since")
    List<String> findDistinctErrorTypesSince(@Param("since") LocalDateTime since);

    boolean existsByEventId(String eventId);
}