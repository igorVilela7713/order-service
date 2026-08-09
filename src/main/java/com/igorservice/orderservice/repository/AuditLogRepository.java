package com.igorservice.orderservice.repository;

import com.igorservice.orderservice.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByOrderIdOrderByTimestampDesc(UUID orderId);

    List<AuditLog> findByEventTypeOrderByTimestampDesc(String eventType);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentEvents(@Param("since") Instant since);

    long countByOrderId(UUID orderId);
}