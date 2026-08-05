package com.igorservice.orderservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMetricsTest {

    private MeterRegistry meterRegistry;
    private OrderMetrics orderMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orderMetrics = new OrderMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should increment orders created counter and record timer")
    void recordOrderCreated() {
        // Act
        orderMetrics.recordOrderCreated(150L);

        // Assert — counter
        Counter createdCounter = meterRegistry.find("orders.created.total").counter();
        assertThat(createdCounter).isNotNull();
        assertThat(createdCounter.count()).isEqualTo(1.0);

        // Assert — timer
        Timer creationTimer = meterRegistry.find("order.creation.duration").timer();
        assertThat(creationTimer).isNotNull();
        assertThat(creationTimer.count()).isEqualTo(1);
        assertThat(creationTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150.0);

        // Act again — verify accumulation
        orderMetrics.recordOrderCreated(200L);
        assertThat(createdCounter.count()).isEqualTo(2.0);
        assertThat(creationTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(350.0);
    }

    @Test
    @DisplayName("Should increment status changed counter with correct tag")
    void recordStatusChanged() {
        // Act
        orderMetrics.recordStatusChanged("CONFIRMED");

        // Assert
        Counter statusCounter = meterRegistry.find("orders.status.changed.total")
            .tag("status", "CONFIRMED")
            .counter();
        assertThat(statusCounter).isNotNull();
        assertThat(statusCounter.count()).isEqualTo(1.0);

        // Act again with same status
        orderMetrics.recordStatusChanged("CONFIRMED");
        assertThat(statusCounter.count()).isEqualTo(2.0);

        // Act with different status — should create separate counter
        orderMetrics.recordStatusChanged("CANCELLED");
        Counter cancelledCounter = meterRegistry.find("orders.status.changed.total")
            .tag("status", "CANCELLED")
            .counter();
        assertThat(cancelledCounter).isNotNull();
        assertThat(cancelledCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should track active order count via gauge")
    void trackActiveOrderCount() {
        // Assert initial value
        assertThat(orderMetrics.getActiveOrderCount()).isEqualTo(0);

        // Act — simulate order creations
        orderMetrics.recordOrderCreated(100L);
        orderMetrics.recordOrderCreated(200L);
        orderMetrics.recordOrderCreated(300L);

        assertThat(orderMetrics.getActiveOrderCount()).isEqualTo(3);

        // Act — simulate order completions
        orderMetrics.recordOrderCompleted();
        assertThat(orderMetrics.getActiveOrderCount()).isEqualTo(2);

        orderMetrics.recordOrderCompleted();
        assertThat(orderMetrics.getActiveOrderCount()).isEqualTo(1);

        orderMetrics.recordOrderCompleted();
        assertThat(orderMetrics.getActiveOrderCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should not go below zero for active order count")
    void activeOrderCountFloorAtZero() {
        // Act — complete more than created
        orderMetrics.recordOrderCreated(100L);
        orderMetrics.recordOrderCompleted();
        orderMetrics.recordOrderCompleted(); // extra

        // Assert
        assertThat(orderMetrics.getActiveOrderCount()).isZero();
    }
}
