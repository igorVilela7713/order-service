package com.igorservice.orderservice.service;

import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.dlq-topic:order.dlq}")
    private String dlqTopic;

    private static final String TOPIC_ORDER_CREATED = "order.created";
    private static final String TOPIC_ORDER_STATUS_CHANGED = "order.status-changed";
    private static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @org.springframework.retry.annotation.Backoff(
            initialDelay = 1000,
            maxDelay = 10000,
            multiplier = 2.0
        )
    )
    public void publishOrderCreated(Order order) {
        var event = buildEvent(order, "ORDER_CREATED");
        publishSync(TOPIC_ORDER_CREATED, order.getId().toString(), event);
    }

    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @org.springframework.retry.annotation.Backoff(
            initialDelay = 1000,
            maxDelay = 10000,
            multiplier = 2.0
        )
    )
    public void publishOrderStatusChanged(Order order, OrderStatus previousStatus) {
        var event = buildStatusChangeEvent(order, "ORDER_STATUS_CHANGED", previousStatus);
        publishSync(TOPIC_ORDER_STATUS_CHANGED, order.getId().toString(), event);
    }

    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @org.springframework.retry.annotation.Backoff(
            initialDelay = 1000,
            maxDelay = 10000,
            multiplier = 2.0
        )
    )
    public void publishOrderCancelled(Order order) {
        var event = buildEvent(order, "ORDER_CANCELLED");
        publishSync(TOPIC_ORDER_CANCELLED, order.getId().toString(), event);
    }

    @Recover
    public void recoverPublishOrderCreated(Exception ex, Order order) {
        log.error("All retries exhausted publishing ORDER_CREATED for order: {}. Sending to DLQ.", order.getOrderNumber(), ex);
        sendToDlq(TOPIC_ORDER_CREATED, order.getId().toString(), buildEvent(order, "ORDER_CREATED"), ex);
    }

    @Recover
    public void recoverPublishOrderStatusChanged(Exception ex, Order order, OrderStatus previousStatus) {
        log.error("All retries exhausted publishing ORDER_STATUS_CHANGED for order: {}. Sending to DLQ.", order.getOrderNumber(), ex);
        sendToDlq(TOPIC_ORDER_STATUS_CHANGED, order.getId().toString(), buildStatusChangeEvent(order, "ORDER_STATUS_CHANGED", previousStatus), ex);
    }

    @Recover
    public void recoverPublishOrderCancelled(Exception ex, Order order) {
        log.error("All retries exhausted publishing ORDER_CANCELLED for order: {}. Sending to DLQ.", order.getOrderNumber(), ex);
        sendToDlq(TOPIC_ORDER_CANCELLED, order.getId().toString(), buildEvent(order, "ORDER_CANCELLED"), ex);
    }

    private void publishSync(String topic, String key, Object event) {
        log.info("Publishing event to topic: {}, key: {}", topic, key);
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        // Block to allow @Retryable to catch exceptions
        future.join();
        log.debug("Event published successfully to topic: {}", topic);
    }

    private void sendToDlq(String originalTopic, String key, Object event, Exception originalException) {
        try {
            var dlqEvent = new java.util.HashMap<>();
            if (event instanceof java.util.Map<?, ?> map) {
                dlqEvent.putAll(map);
            }
            dlqEvent.put("dlq.originalTopic", originalTopic);
            dlqEvent.put("dlq.failureReason", originalException.getMessage());
            dlqEvent.put("dlq.failedAt", java.time.Instant.now().toString());

            kafkaTemplate.send(dlqTopic, key, dlqEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to DLQ topic {}: {}", dlqTopic, ex.getMessage(), ex);
                    } else {
                        log.warn("Event sent to DLQ topic: {}, key: {}", dlqTopic, key);
                    }
                });
        } catch (Exception e) {
            log.error("Error sending event to DLQ: {}", e.getMessage(), e);
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
