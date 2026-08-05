package com.igorservice.orderservice.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaDlqListener — DLQ message handling")
class KafkaDlqListenerTest {

    private KafkaDlqListener listener;

    @BeforeEach
    void setUp() {
        listener = new KafkaDlqListener();
    }

    @Test
    @DisplayName("listenDlq should log Map-type event without throwing")
    void listenDlq_withMapEvent_logsSuccessfully() {
        // Arrange
        Map<String, Object> event = Map.of(
            "eventType", "ORDER_CREATED",
            "orderId", "abc-123",
            "orderNumber", "ORD-20260805-00001",
            "customerId", "customer-001"
        );
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
            "order.dlq", 0, 0L, "abc-123", event);

        // Act & Assert
        assertThatCode(() -> listener.listen(record)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("listenDlq should log non-Map event without throwing")
    void listenDlq_withNonMapEvent_logsSuccessfully() {
        // Arrange
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
            "order.dlq", 0, 0L, "key-1", "raw-string-payload");

        // Act & Assert
        assertThatCode(() -> listener.listen(record)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("listenDlq should handle null value without throwing")
    void listenDlq_withNullValue_logsSuccessfully() {
        // Arrange
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
            "order.dlq", 0, 0L, "key-2", null);

        // Act & Assert
        assertThatCode(() -> listener.listen(record)).doesNotThrowAnyException();
    }
}
