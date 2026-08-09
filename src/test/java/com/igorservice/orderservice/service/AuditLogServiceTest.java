package com.igorservice.orderservice.service;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

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
    @DisplayName("Should get audit log for order")
    void getAuditLogForOrder_Success() {
        // Arrange
        AuditLog log1 = AuditLog.builder().id(UUID.randomUUID()).orderId(testOrder.getId()).build();
        AuditLog log2 = AuditLog.builder().id(UUID.randomUUID()).orderId(testOrder.getId()).build();
        when(auditLogRepository.findByOrderIdOrderByTimestampDesc(testOrder.getId())).thenReturn(List.of(log1, log2));

        // Act
        List<AuditLog> result = auditLogService.getAuditLogForOrder(testOrder.getId());

        // Assert
        assertThat(result).hasSize(2);
        verify(auditLogRepository).findByOrderIdOrderByTimestampDesc(testOrder.getId());
    }

    @Test
    @DisplayName("Should get audit logs by event type")
    void getAuditLogsByEventType_Success() {
        // Arrange
        AuditLog log = AuditLog.builder().id(UUID.randomUUID()).eventType("ORDER_CREATED").build();
        when(auditLogRepository.findByEventTypeOrderByTimestampDesc("ORDER_CREATED")).thenReturn(List.of(log));

        // Act
        List<AuditLog> result = auditLogService.getAuditLogsByEventType("ORDER_CREATED");

        // Assert
        assertThat(result).hasSize(1);
        verify(auditLogRepository).findByEventTypeOrderByTimestampDesc("ORDER_CREATED");
    }
}