package com.igorservice.orderservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class KafkaDlqListener {

    private static final String DLQ_TOPIC = "order.dlq";

    @KafkaListener(
        topics = DLQ_TOPIC,
        groupId = "order-service-dlq",
        properties = {
            "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
            "spring.json.value.default.type=java.util.Map"
        }
    )
    public void listen(ConsumerRecord<String, Object> record) {
        log.warn("DLQ message received — topic: {}, partition: {}, offset: {}, key: {}",
            record.topic(), record.partition(), record.offset(), record.key());

        if (record.value() instanceof Map<?, ?> event) {
            log.warn("DLQ event details — eventType: {}, orderId: {}, orderNumber: {}, customerId: {}",
                event.get("eventType"),
                event.get("orderId"),
                event.get("orderNumber"),
                event.get("customerId"));
        } else {
            log.warn("DLQ event payload: {}", record.value());
        }

        // TODO: Alerting / metrics / manual retry UI integration
        log.info("DLQ message processed and logged for later investigation");
    }
}
