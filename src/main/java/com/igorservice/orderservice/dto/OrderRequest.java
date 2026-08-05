package com.igorservice.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new order")
public class OrderRequest {

    @NotBlank(message = "Customer ID is required")
    @Size(max = 100, message = "Customer ID must be at most 100 characters")
    @Schema(description = "Customer identifier", example = "customer-001")
    private String customerId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    @Schema(description = "List of order items")
    private List<OrderItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Individual order item")
    public static class OrderItemRequest {

        @NotBlank(message = "Product ID is required")
        @Size(max = 100)
        @Schema(description = "Product identifier", example = "PROD-001")
        private String productId;

        @Size(max = 200)
        @Schema(description = "Product display name", example = "Widget Pro")
        private String productName;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Schema(description = "Quantity to order", example = "2")
        private Integer quantity;

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be positive")
        @Schema(description = "Price per unit", example = "29.99")
        private BigDecimal unitPrice;
    }
}
