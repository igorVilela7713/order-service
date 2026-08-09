package com.igorservice.orderservice.service;

import com.igorservice.orderservice.metrics.OrderMetrics;
import com.igorservice.orderservice.model.AuditLog;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final OrderMetrics orderMetrics;

    @Transactional
    public void logOrderCreated(Order order, String actorId) {
        log.info("Audit: Order created - orderId={}, orderNumber={}, actorId={}",
                order.getId(), order.getOrderNumber(), actorId);

        AuditLog auditLog = AuditLog.builder()
                .orderId(order.getId())
                .eventType("ORDER_CREATED")
                .eventData(buildEventData(order))
                .newStatus(OrderStatus.PENDING.name())
                .actorId(actorId)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void logOrderStatusChanged(Order order, OrderStatus previousStatus, String actorId) {
        log.info("Audit: Order status changed - orderId={}, orderNumber={}, from={}, to={}, actorId={}",
                order.getId(), order.getOrderNumber(), previousStatus, order.getStatus(), actorId);

        AuditLog auditLog = AuditLog.builder()
                .orderId(order.getId())
                .eventType("ORDER_STATUS_CHANGED")
                .eventData(buildEventData(order))
                .previousStatus(previousStatus.name())
                .newStatus(order.getStatus().name())
                .actorId(actorId)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void logOrderCancelled(Order order, OrderStatus previousStatus, String actorId) {
        log.info("Audit: Order cancelled - orderId={}, orderNumber={}, previousStatus={}, actorId={}",
                order.getId(), order.getOrderNumber(), previousStatus, actorId);

        AuditLog auditLog = AuditLog.builder()
                .orderId(order.getId())
                .eventType("ORDER_CANCELLED")
                .eventData(buildEventData(order))
                .previousStatus(previousStatus.name())
                .newStatus(OrderStatus.CANCELLED.name())
                .actorId(actorId)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogForOrder(UUID orderId, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        Page<AuditLog> result = auditLogRepository.findByOrderId(orderId, pageable);
        orderMetrics.recordAuditLogQuery("byOrderId", System.currentTimeMillis() - startTime);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByEventType(String eventType, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        Page<AuditLog> result = auditLogRepository.findByEventType(eventType, pageable);
        orderMetrics.recordAuditLogQuery("byEventType", System.currentTimeMillis() - startTime);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getRecentAuditLogs(Instant since, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        Page<AuditLog> result = auditLogRepository.findRecentEvents(since, pageable);
        orderMetrics.recordAuditLogQuery("recentEvents", System.currentTimeMillis() - startTime);
        return result;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLatestAuditLogsForOrder(UUID orderId) {
        long startTime = System.currentTimeMillis();
        List<AuditLog> result = auditLogRepository.findTop10ByOrderIdOrderByTimestampDesc(orderId);
        orderMetrics.recordAuditLogQuery("latestByOrderId", System.currentTimeMillis() - startTime);
        return result;
    }

    @Transactional(readOnly = true)
    public long countAuditLogsForOrder(UUID orderId) {
        long startTime = System.currentTimeMillis();
        long count = auditLogRepository.countByOrderId(orderId);
        orderMetrics.recordAuditLogQuery("countByOrderId", System.currentTimeMillis() - startTime);
        return count;
    }

    @Transactional(readOnly = true)
    public long countAuditLogsByEventType(String eventType) {
        long startTime = System.currentTimeMillis();
        long count = auditLogRepository.countByEventType(eventType);
        orderMetrics.recordAuditLogQuery("countByEventType", System.currentTimeMillis() - startTime);
        return count;
    }

    private String buildEventData(Order order) {
        return Map.of(
                "orderId", order.getId().toString(),
                "orderNumber", order.getOrderNumber(),
                "customerId", order.getCustomerId(),
                "totalAmount", order.getTotalAmount(),
                "status", order.getStatus().name(),
                "items", order.getItems().stream()
                        .map(item -> Map.of(
                                "productId", item.getProductId(),
                                "productName", item.getProductName(),
                                "quantity", item.getQuantity(),
                                "unitPrice", item.getUnitPrice()
                        ))
                        .toList()
        ).toString();
    }
}