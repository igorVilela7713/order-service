package com.igorservice.orderservice.controller;

import com.igorservice.orderservice.dto.OrderRequest;
import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order management operations")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order", responses = {
        @ApiResponse(responseCode = "201", description = "Order created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("POST /api/v1/orders - customer: {}", request.getCustomerId());
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all orders (paginated)")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        size = Math.min(size, 100); // Cap at 100
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        log.debug("GET /api/v1/orders - page={}, size={}", page, size);
        return ResponseEntity.ok(orderService.listOrders(pageable));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID orderId) {
        log.debug("GET /api/v1/orders/{}", orderId);
        return ResponseEntity.ok(orderService.getOrderById(orderId));
    }

    @PutMapping("/{orderId}/status")
    @Operation(summary = "Update order status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody StatusUpdateRequest statusRequest) {
        log.info("PUT /api/v1/orders/{}/status -> {}", orderId, statusRequest.getStatus());
        OrderStatus newStatus = OrderStatus.valueOf(statusRequest.getStatus());
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, newStatus));
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID orderId) {
        log.info("DELETE /api/v1/orders/{}", orderId);
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    // Inner DTO for status update
    public record StatusUpdateRequest(
        @jakarta.validation.constraints.NotNull String status
    ) {}
}
