package com.igorservice.orderservice.service;

import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaEventPublisher publisher;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "dlqTopic", "order.dlq");

        testOrder = Order.builder()
            .id(UUID.randomUUID())
            .orderNumber("ORD-20260805-00001")
            .customerId("customer-001")
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("109.97"))
            .build();
    }

    @Test
    @DisplayName("Should publish order created event to correct topic")
    void publishOrderCreated() {
        // Arrange
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("order.created"), eq(testOrder.getId().toString()), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        publisher.publishOrderCreated(testOrder);

        // Assert
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("order.created"), eq(testOrder.getId().toString()), eventCaptor.capture());

        Map<String, Object> capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.get("eventType")).isEqualTo("ORDER_CREATED");
        assertThat(capturedEvent.get("orderId")).isEqualTo(testOrder.getId().toString());
        assertThat(capturedEvent.get("orderNumber")).isEqualTo("ORD-20260805-00001");
        assertThat(capturedEvent.get("customerId")).isEqualTo("customer-001");
        assertThat(capturedEvent.get("status")).isEqualTo("PENDING");
        assertThat(capturedEvent.get("eventId")).isNotNull();
        assertThat(capturedEvent.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should publish order status changed event with previous status")
    void publishOrderStatusChanged() {
        // Arrange
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("order.status-changed"), eq(testOrder.getId().toString()), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        publisher.publishOrderStatusChanged(testOrder, OrderStatus.PENDING);

        // Assert
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("order.status-changed"), eq(testOrder.getId().toString()), eventCaptor.capture());

        Map<String, Object> capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.get("eventType")).isEqualTo("ORDER_STATUS_CHANGED");
        assertThat(capturedEvent.get("previousStatus")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should publish order cancelled event")
    void publishOrderCancelled() {
        // Arrange
        testOrder.setStatus(OrderStatus.CANCELLED);
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("order.cancelled"), eq(testOrder.getId().toString()), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        publisher.publishOrderCancelled(testOrder);

        // Assert
        verify(kafkaTemplate).send(eq("order.cancelled"), eq(testOrder.getId().toString()), any());
    }

    @Test
    @DisplayName("Should send to DLQ when all retries exhausted for order created")
    void recoverPublishOrderCreated_SendsToDlq() {
        // Arrange
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("order.dlq"), eq(testOrder.getId().toString()), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act — call the @Recover method directly
        publisher.recoverPublishOrderCreated(
            new RuntimeException("Simulated Kafka failure"),
            testOrder
        );

        // Assert — verify DLQ publish
        ArgumentCaptor<Map> dlqCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("order.dlq"), eq(testOrder.getId().toString()), dlqCaptor.capture());

        Map<String, Object> dlqEvent = dlqCaptor.getValue();
        assertThat(dlqEvent.get("dlq.originalTopic")).isEqualTo("order.created");
        assertThat(dlqEvent.get("dlq.failureReason")).isEqualTo("Simulated Kafka failure");
        assertThat(dlqEvent.get("dlq.failedAt")).isNotNull();
        assertThat(dlqEvent.get("eventType")).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("Should send to DLQ when all retries exhausted for status changed")
    void recoverPublishOrderStatusChanged_SendsToDlq() {
        // Arrange
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("order.dlq"), eq(testOrder.getId().toString()), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        publisher.recoverPublishOrderStatusChanged(
            new RuntimeException("Connection refused"),
            testOrder,
            OrderStatus.PENDING
        );

        // Assert
        ArgumentCaptor<Map> dlqCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("order.dlq"), eq(testOrder.getId().toString()), dlqCaptor.capture());

        Map<String, Object> dlqEvent = dlqCaptor.getValue();
        assertThat(dlqEvent.get("dlq.originalTopic")).isEqualTo("order.status-changed");
        assertThat(dlqEvent.get("previousStatus")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should send to DLQ when all retries exhausted for cancelled")
    void recoverPublishOrderCancelled_SendsToDlq() {
        // Arrange
        testOrder.setStatus(OrderStatus.CANCELLED);
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("order.dlq"), eq(testOrder.getId().toString()), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        publisher.recoverPublishOrderCancelled(
            new RuntimeException("Broker unavailable"),
            testOrder
        );

        // Assert
        verify(kafkaTemplate).send(eq("order.dlq"), eq(testOrder.getId().toString()), any());
    }

    @Test
    @DisplayName("Should not send to DLQ when original publish succeeds")
    void publishOrderCreated_NoDlqOnSuccess() {
        // Arrange
        SendResult<String, Object> mockResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("order.created"), eq(testOrder.getId().toString()), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        // Act
        publisher.publishOrderCreated(testOrder);

        // Assert — only one send to original topic, zero to DLQ
        verify(kafkaTemplate).send(eq("order.created"), anyString(), any());
        verify(kafkaTemplate, never()).send(eq("order.dlq"), anyString(), any());
    }
}
