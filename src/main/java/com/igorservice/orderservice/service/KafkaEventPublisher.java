package com.igorservice.orderservice.service;

import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // TODO: Extract to a constants class or configuration property
    private static final String TOPIC_ORDER_CREATED = "order.created";
    private static final String TOPIC_ORDER_STATUS_CHANGED = "order.status-changed";
    private static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    public void publishOrderCreated(Order order) {
        var event = buildEvent(order, "ORDER_CREATED");
        publish(TOPIC_ORDER_CREATED, order.getId().toString(), event);
    }

    public void publishOrderStatusChanged(Order order, OrderStatus previousStatus) {
        var event = buildStatusChangeEvent(order, "ORDER_STATUS_CHANGED", previousStatus);
        publish(TOPIC_ORDER_STATUS_CHANGED, order.getId().toString(), event);
    }

    public void publishOrderCancelled(Order order) {
        var event = buildEvent(order, "ORDER_CANCELLED");
        publish(TOPIC_ORDER_CANCELLED, order.getId().toString(), event);
    }

    private void publish(String topic, String key, Object event) {
        log.info("Publishing event to topic: {}, key: {}", topic, key);
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage(), ex);
                    // TODO: Add retry logic or dead-letter queue
                } else {
                    log.debug("Event published to topic {} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing event to topic {}: {}", topic, e.getMessage(), e);
            // TODO: Consider circuit breaker pattern
        }
    }

    private java.util.Map<String, Object> buildEvent(Order order, String eventType) {
        return java.util.Map.of(
            "eventId", UUID.randomUUID().toString(),
            "eventType", eventType,
            "orderId", order.getId().toString(),
            "orderNumber", order.getOrderNumber(),
            "customerId", order.getCustomerId(),
            "totalAmount", order.getTotalAmount(),
            "status", order.getStatus().name(),
            "timestamp", java.time.Instant.now().toString()
        );
    }

    private java.util.Map<String, Object> buildStatusChangeEvent(Order order, String eventType, OrderStatus previousStatus) {
        var event = new java.util.HashMap<>(buildEvent(order, eventType));
        event.put("previousStatus", previousStatus.name());
        return event;
    }
}
