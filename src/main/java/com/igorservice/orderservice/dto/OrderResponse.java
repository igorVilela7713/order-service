package com.igorservice.orderservice.dto;

import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order response")
public class OrderResponse {

    @Schema(description = "Order ID")
    private UUID id;

    @Schema(description = "Order number", example = "ORD-20260805-00001")
    private String orderNumber;

    @Schema(description = "Customer identifier")
    private String customerId;

    @Schema(description = "Order status")
    private String status;

    @Schema(description = "Total order amount")
    private BigDecimal totalAmount;

    @Schema(description = "Order items")
    private List<OrderItemResponse> items;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    public static OrderResponse fromEntity(Order order) {
        return OrderResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .customerId(order.getCustomerId())
            .status(order.getStatus().name())
            .totalAmount(order.getTotalAmount())
            .items(order.getItems().stream().map(OrderItemResponse::fromEntity).toList())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private UUID id;
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public static OrderItemResponse fromEntity(OrderItem item) {
            return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
        }
    }
}
