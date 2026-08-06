package com.igorservice.orderservice.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Override
    public Health health() {
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 5000);

            try (AdminClient adminClient = AdminClient.create(props)) {
                var clusterIdResult = adminClient.describeCluster();
                String clusterId = clusterIdResult.clusterId().get(10, TimeUnit.SECONDS);
                int nodeCount = clusterIdResult.nodes().get(10, TimeUnit.SECONDS).size();

                return Health.up()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodeCount", nodeCount)
                    .build();
            }
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("bootstrapServers", bootstrapServers)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
