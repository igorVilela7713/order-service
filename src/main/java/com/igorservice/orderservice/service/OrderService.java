package com.igorservice.orderservice.service;

import com.igorservice.orderservice.dto.OrderRequest;
import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.exception.OrderNotFoundException;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderItem;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final AtomicLong orderSequence = new AtomicLong(0);

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
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

        kafkaEventPublisher.publishOrderCreated(saved);
        return OrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(java.util.UUID orderId) {
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
    public OrderResponse updateOrderStatus(java.util.UUID orderId, OrderStatus newStatus) {
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

        kafkaEventPublisher.publishOrderStatusChanged(saved, oldStatus);
        return OrderResponse.fromEntity(saved);
    }

    @Transactional
    public void cancelOrder(java.util.UUID orderId) {
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

        kafkaEventPublisher.publishOrderCancelled(saved);
        log.info("Order {} cancelled", orderId);
    }

    private String generateOrderNumber() {
        String dateStr = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = orderSequence.incrementAndGet();
        return String.format("ORD-%s-%05d", dateStr, seq);
    }
}
