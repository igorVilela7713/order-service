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
    private final Timer orderCreationTimer;
    private final Counter auditLogQueriesCounter;
    private final Timer auditLogQueryTimer;
    private final AtomicLong activeOrderCount = new AtomicLong(0);
    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ordersCreatedCounter = Counter.builder("orders.created.total")
            .description("Total number of orders created")
            .register(registry);

        this.orderCreationTimer = Timer.builder("order.creation.duration")
            .description("Duration of order creation in milliseconds")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.auditLogQueriesCounter = Counter.builder("audit.log.queries.total")
            .description("Total number of audit log queries")
            .register(registry);

        this.auditLogQueryTimer = Timer.builder("audit.log.query.duration")
            .description("Duration of audit log queries in milliseconds")
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
            .register(registry)
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

    public void recordAuditLogQuery(String queryType, long durationMs) {
        auditLogQueriesCounter.increment(1.0);
        auditLogQueryTimer.record(durationMs, TimeUnit.MILLISECONDS);
        log.debug("Recorded audit log query: type={}, duration={}ms", queryType, durationMs);
    }

    public void setActiveOrderCount(long count) {
        activeOrderCount.set(count);
    }

    public long getActiveOrderCount() {
        return activeOrderCount.get();
    }
}