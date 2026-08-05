package com.igorservice.orderservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class OrderMetrics {

    private final Counter ordersCreatedCounter;
    private final Counter ordersStatusChangedCounter;
    private final Timer orderCreationTimer;
    private final AtomicLong activeOrderCount = new AtomicLong(0);

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreatedCounter = Counter.builder("orders.created.total")
            .description("Total number of orders created")
            .register(registry);

        this.ordersStatusChangedCounter = Counter.builder("orders.status.changed.total")
            .description("Total number of order status changes")
            .tag("status", "unknown") // default tag, overridden at record time
            .register(registry);

        this.orderCreationTimer = Timer.builder("order.creation.duration")
            .description("Duration of order creation in milliseconds")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        Gauge.builder("orders.active.count", activeOrderCount, AtomicLong::doubleValue)
            .description("Number of active orders (not DELIVERED or CANCELLED)")
            .register(registry);

        log.info("OrderMetrics initialized");
    }

    public void recordOrderCreated(long durationMs) {
        ordersCreatedCounter.increment();
        orderCreationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        activeOrderCount.incrementAndGet();
        log.debug("Recorded order creation: duration={}ms, activeCount={}", durationMs, activeOrderCount.get());
    }

    public void recordStatusChanged(String status) {
        Counter.builder("orders.status.changed.total")
            .description("Total number of order status changes")
            .tag("status", status)
            .register(ordersCreatedCounter.registry())
            .increment();
        log.debug("Recorded status change to: {}", status);
    }

    public void recordOrderCompleted() {
        long current = activeOrderCount.decrementAndGet();
        if (current < 0) {
            activeOrderCount.set(0);
        }
        log.debug("Order completed, activeCount={}", activeOrderCount.get());
    }

    public void setActiveOrderCount(long count) {
        activeOrderCount.set(count);
    }

    public long getActiveOrderCount() {
        return activeOrderCount.get();
    }
}
