package com.igorservice.orderservice.service;

import com.igorservice.orderservice.dto.OrderRequest;
import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.exception.OrderNotFoundException;
import com.igorservice.orderservice.metrics.OrderMetrics;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderItem;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final OrderMetrics orderMetrics;
    private final AtomicLong orderSequence = new AtomicLong(0);

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("spanId", UUID.randomUUID().toString().substring(0, 8));

        try {
            log.info("Creating order for customer: {}", request.getCustomerId());

            Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .build();

            for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
                OrderItem item = OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .build();
                order.addItem(item);
            }

            order.recalculateTotal();
            Order saved = orderRepository.save(order);
            log.info("Order created: {} with total: {}", saved.getOrderNumber(), saved.getTotalAmount());

            // Record metrics
            long duration = System.currentTimeMillis() - startTime;
            orderMetrics.recordOrderCreated(duration);

            kafkaEventPublisher.publishOrderCreated(saved);
            return OrderResponse.fromEntity(saved);
        } finally {
            MDC.clear();
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        log.debug("Fetching order: {}", orderId);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> {
                log.warn("Order not found: {}", orderId);
                return new OrderNotFoundException("Order not found with ID: " + orderId);
            });
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(Pageable pageable) {
        log.debug("Listing orders, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return orderRepository.findAll(pageable).map(OrderResponse::fromEntity);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        MDC.put("traceId", UUID.randomUUID().toString());
        MDC.put("spanId", UUID.randomUUID().toString().substring(0, 8));

        try {
            log.info("Updating order {} status to {}", orderId, newStatus);

            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));

            if (!order.getStatus().canTransitionTo(newStatus)) {
                throw new IllegalStateException(
                    String.format("Invalid status transition from %s to %s", order.getStatus(), newStatus)
                );
            }

            OrderStatus oldStatus = order.getStatus();
            order.setStatus(newStatus);
            Order saved = orderRepository.save(order);

            // Record metrics for status change
            orderMetrics.recordStatusChanged(newStatus.name());

            // Record order completion for active count gauge
            if (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED) {
                orderMetrics.recordOrderCompleted();
            }

            kafkaEventPublisher.publishOrderStatusChanged(saved, oldStatus);
            return OrderResponse.fromEntity(saved);
        } finally {
            MDC.clear();
        }
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        MDC.put("traceId", UUID.randomUUID().toString());
        MDC.put("spanId", UUID.randomUUID().toString().substring(0, 8));

        try {
            log.info("Cancelling order: {}", orderId);
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));

            if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
                throw new IllegalStateException(
                    "Order cannot be cancelled in status: " + order.getStatus()
                );
            }

            OrderStatus oldStatus = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            Order saved = orderRepository.save(order);

            // Record metrics
            orderMetrics.recordStatusChanged(OrderStatus.CANCELLED.name());
            orderMetrics.recordOrderCompleted();

            kafkaEventPublisher.publishOrderCancelled(saved);
            log.info("Order {} cancelled", orderId);
        } finally {
            MDC.clear();
        }
    }

    private String generateOrderNumber() {
        String dateStr = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = orderSequence.incrementAndGet();
        return String.format("ORD-%s-%05d", dateStr, seq);
    }
}
