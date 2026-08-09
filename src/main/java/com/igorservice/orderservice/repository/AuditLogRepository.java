package com.igorservice.orderservice.repository;

import com.igorservice.orderservice.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOrderId(UUID orderId, Pageable pageable);

    Page<AuditLog> findByEventType(String eventType, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    Page<AuditLog> findRecentEvents(@Param("since") Instant since, Pageable pageable);

    long countByOrderId(UUID orderId);

    long countByEventType(String eventType);

    List<AuditLog> findTop10ByOrderIdOrderByTimestampDesc(UUID orderId);
}