package com.igorservice.orderservice.service;

import com.igorservice.orderservice.model.AuditLog;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public List<AuditLog> getAuditLogForOrder(UUID orderId) {
        return auditLogRepository.findByOrderIdOrderByTimestampDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByEventType(String eventType) {
        return auditLogRepository.findByEventTypeOrderByTimestampDesc(eventType);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentAuditLogs(Instant since) {
        return auditLogRepository.findRecentEvents(since);
    }

    @Transactional(readOnly = true)
    public long countAuditLogsForOrder(UUID orderId) {
        return auditLogRepository.countByOrderId(orderId);
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