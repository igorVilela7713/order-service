package com.igorservice.orderservice.builder;

import com.igorservice.orderservice.dto.OrderRequest;
import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderItem;
import com.igorservice.orderservice.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderTestDataBuilder {

    private UUID id = UUID.randomUUID();
    private String orderNumber = "ORD-20260805-00001";
    private String customerId = "customer-001";
    private OrderStatus status = OrderStatus.PENDING;
    private BigDecimal totalAmount = new BigDecimal("100.00");
    private List<OrderItem> items = new ArrayList<>();
    private Instant createdAt = Instant.parse("2026-08-05T10:00:00Z");
    private Instant updatedAt = Instant.parse("2026-08-05T10:00:00Z");

    private OrderTestDataBuilder() {}

    public static OrderTestDataBuilder anOrder() {
        return new OrderTestDataBuilder();
    }

    public OrderTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public OrderTestDataBuilder withOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    public OrderTestDataBuilder withCustomerId(String customerId) {
        this.customerId = customerId;
        return this;
    }

    public OrderTestDataBuilder withStatus(OrderStatus status) {
        this.status = status;
        return this;
    }

    public OrderTestDataBuilder withTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public OrderTestDataBuilder withItems(List<OrderItem> items) {
        this.items = new ArrayList<>(items);
        return this;
    }

    public OrderTestDataBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public OrderTestDataBuilder withUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Order build() {
        Order order = Order.builder()
            .id(id)
            .orderNumber(orderNumber)
            .customerId(customerId)
            .status(status)
            .totalAmount(totalAmount)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

        for (OrderItem item : items) {
            order.addItem(item);
        }

        return order;
    }

    // --- Common scenario builders ---

    public static Order buildPendingOrder() {
        return anOrder()
            .withStatus(OrderStatus.PENDING)
            .withTotalAmount(new BigDecimal("59.98"))
            .withItems(List.of(
                OrderTestDataBuilder.buildOrderItem("PROD-001", "Widget Pro", 2, new BigDecimal("29.99"))))
            .build();
    }

    public static Order buildConfirmedOrder() {
        return anOrder()
            .withStatus(OrderStatus.CONFIRMED)
            .withOrderNumber("ORD-20260805-00002")
            .withTotalAmount(new BigDecimal("149.97"))
            .withItems(List.of(
                OrderTestDataBuilder.buildOrderItem("PROD-001", "Widget Pro", 2, new BigDecimal("29.99")),
                OrderTestDataBuilder.buildOrderItem("PROD-002", "Gadget Plus", 1, new BigDecimal("89.99"))))
            .build();
    }

    public static Order buildDeliveredOrder() {
        return anOrder()
            .withStatus(OrderStatus.DELIVERED)
            .withOrderNumber("ORD-20260805-00003")
            .withTotalAmount(new BigDecimal("49.99"))
            .withItems(List.of(
                OrderTestDataBuilder.buildOrderItem("PROD-003", "Basic Item", 1, new BigDecimal("49.99"))))
            .build();
    }

    public static Order buildCancelledOrder() {
        return anOrder()
            .withStatus(OrderStatus.CANCELLED)
            .withOrderNumber("ORD-20260805-00004")
            .withTotalAmount(new BigDecimal("29.99"))
            .withItems(List.of(
                OrderTestDataBuilder.buildOrderItem("PROD-004", "Cancelled Item", 1, new BigDecimal("29.99"))))
            .build();
    }

    public static OrderItem buildOrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
        return OrderItem.builder()
            .id(UUID.randomUUID())
            .productId(productId)
            .productName(productName)
            .quantity(quantity)
            .unitPrice(unitPrice)
            .build();
    }

    // --- Response builder ---

    public static OrderResponse buildOrderResponse(Order order) {
        return OrderResponse.fromEntity(order);
    }

    public static OrderResponse buildPendingOrderResponse() {
        return OrderResponse.fromEntity(buildPendingOrder());
    }

    // --- Request builder ---

    public static OrderRequest buildPendingOrderRequest() {
        return OrderRequest.builder()
            .customerId("customer-001")
            .items(List.of(
                OrderRequest.OrderItemRequest.builder()
                    .productId("PROD-001")
                    .productName("Widget Pro")
                    .quantity(2)
                    .unitPrice(new BigDecimal("29.99"))
                    .build()))
            .build();
    }
}
