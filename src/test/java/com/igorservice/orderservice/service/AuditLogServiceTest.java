package com.igorservice.orderservice.service;

import com.igorservice.orderservice.metrics.OrderMetrics;
import com.igorservice.orderservice.model.AuditLog;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private OrderMetrics orderMetrics;

    @InjectMocks
    private AuditLogService auditLogService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("ORD-20260805-00001")
                .customerId("customer-001")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("109.97"))
                .items(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should log order created event")
    void logOrderCreated_Success() {
        // Act
        auditLogService.logOrderCreated(testOrder, "actor-123");

        // Assert
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(testOrder.getId());
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getNewStatus()).isEqualTo("PENDING");
        assertThat(saved.getActorId()).isEqualTo("actor-123");
        assertThat(saved.getEventData()).isNotNull();
    }

    @Test
    @DisplayName("Should log order status changed event")
    void logOrderStatusChanged_Success() {
        // Act
        auditLogService.logOrderStatusChanged(testOrder, OrderStatus.PENDING, "actor-456");

        // Assert
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(testOrder.getId());
        assertThat(saved.getEventType()).isEqualTo("ORDER_STATUS_CHANGED");
        assertThat(saved.getPreviousStatus()).isEqualTo("PENDING");
        assertThat(saved.getNewStatus()).isEqualTo("PENDING");
        assertThat(saved.getActorId()).isEqualTo("actor-456");
    }

    @Test
    @DisplayName("Should log order cancelled event")
    void logOrderCancelled_Success() {
        // Act
        auditLogService.logOrderCancelled(testOrder, OrderStatus.CONFIRMED, "actor-789");

        // Assert
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(testOrder.getId());
        assertThat(saved.getEventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(saved.getPreviousStatus()).isEqualTo("CONFIRMED");
        assertThat(saved.getNewStatus()).isEqualTo("CANCELLED");
        assertThat(saved.getActorId()).isEqualTo("actor-789");
    }

    @Test
    @DisplayName("Should get audit log for order with pagination")
    void getAuditLogForOrder_Success() {
        // Arrange
        AuditLog log1 = AuditLog.builder().id(UUID.randomUUID()).orderId(testOrder.getId()).build();
        AuditLog log2 = AuditLog.builder().id(UUID.randomUUID()).orderId(testOrder.getId()).build();
        Page<AuditLog> page = new PageImpl<>(List.of(log1, log2));
        when(auditLogRepository.findByOrderId(eq(testOrder.getId()), any(Pageable.class))).thenReturn(page);

        // Act
        Page<AuditLog> result = auditLogService.getAuditLogForOrder(testOrder.getId(), Pageable.unpaged());

        // Assert
        assertThat(result.getContent()).hasSize(2);
        verify(auditLogRepository).findByOrderId(eq(testOrder.getId()), any(Pageable.class));
        verify(orderMetrics).recordAuditLogQuery(eq("byOrderId"), anyLong());
    }

    @Test
    @DisplayName("Should get audit logs by event type with pagination")
    void getAuditLogsByEventType_Success() {
        // Arrange
        AuditLog log = AuditLog.builder().id(UUID.randomUUID()).eventType("ORDER_CREATED").build();
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findByEventType(eq("ORDER_CREATED"), any(Pageable.class))).thenReturn(page);

        // Act
        Page<AuditLog> result = auditLogService.getAuditLogsByEventType("ORDER_CREATED", Pageable.unpaged());

        // Assert
        assertThat(result.getContent()).hasSize(1);
        verify(auditLogRepository).findByEventType(eq("ORDER_CREATED"), any(Pageable.class));
        verify(orderMetrics).recordAuditLogQuery(eq("byEventType"), anyLong());
    }

    @Test
    @DisplayName("Should get latest audit logs for order")
    void getLatestAuditLogsForOrder_Success() {
        // Arrange
        AuditLog log1 = AuditLog.builder().id(UUID.randomUUID()).orderId(testOrder.getId()).build();
        AuditLog log2 = AuditLog.builder().id(UUID.randomUUID()).orderId(testOrder.getId()).build();
        when(auditLogRepository.findTop10ByOrderIdOrderByTimestampDesc(testOrder.getId())).thenReturn(List.of(log1, log2));

        // Act
        List<AuditLog> result = auditLogService.getLatestAuditLogsForOrder(testOrder.getId());

        // Assert
        assertThat(result).hasSize(2);
        verify(auditLogRepository).findTop10ByOrderIdOrderByTimestampDesc(testOrder.getId());
        verify(orderMetrics).recordAuditLogQuery(eq("latestByOrderId"), anyLong());
    }

    @Test
    @DisplayName("Should count audit logs for order")
    void countAuditLogsForOrder_Success() {
        // Arrange
        when(auditLogRepository.countByOrderId(testOrder.getId())).thenReturn(5L);

        // Act
        long count = auditLogService.countAuditLogsForOrder(testOrder.getId());

        // Assert
        assertThat(count).isEqualTo(5L);
        verify(auditLogRepository).countByOrderId(testOrder.getId());
        verify(orderMetrics).recordAuditLogQuery(eq("countByOrderId"), anyLong());
    }

    @Test
    @DisplayName("Should count audit logs by event type")
    void countAuditLogsByEventType_Success() {
        // Arrange
        when(auditLogRepository.countByEventType("ORDER_CREATED")).thenReturn(10L);

        // Act
        long count = auditLogService.countAuditLogsByEventType("ORDER_CREATED");

        // Assert
        assertThat(count).isEqualTo(10L);
        verify(auditLogRepository).countByEventType("ORDER_CREATED");
        verify(orderMetrics).recordAuditLogQuery(eq("countByEventType"), anyLong());
    }
}