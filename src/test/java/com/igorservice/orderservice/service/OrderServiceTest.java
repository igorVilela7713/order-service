package com.igorservice.orderservice.service;

import com.igorservice.orderservice.dto.OrderRequest;
import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.exception.OrderNotFoundException;
import com.igorservice.orderservice.metrics.OrderMetrics;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private OrderMetrics orderMetrics;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private OrderRequest testRequest;

    @BeforeEach
    void setUp() {
        testOrder = Order.builder()
            .id(UUID.randomUUID())
            .orderNumber("ORD-20260805-00001")
            .customerId("customer-001")
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("109.97"))
            .items(List.of())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        testRequest = new OrderRequest();
        testRequest.setCustomerId("customer-001");
        testRequest.setItems(List.of(
            new OrderRequest.OrderItemRequest("PROD-001", "Widget Pro", 2, new BigDecimal("29.99")),
            new OrderRequest.OrderItemRequest("PROD-002", "Gadget Plus", 1, new BigDecimal("49.99"))
        ));
    }

    @Test
    @DisplayName("Should create order successfully")
    void createOrder_Success() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        // Act
        OrderResponse response = orderService.createOrder(testRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getCustomerId()).isEqualTo("customer-001");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("109.97"));
        verify(orderRepository).save(any(Order.class));
        verify(kafkaEventPublisher).publishOrderCreated(any(Order.class));
    }

    @Test
    @DisplayName("Should get order by ID")
    void getOrderById_Success() {
        // Arrange
        when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.of(testOrder));

        // Act
        OrderResponse response = orderService.getOrderById(testOrder.getId());

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testOrder.getId());
        assertThat(response.getOrderNumber()).isEqualTo("ORD-20260805-00001");
    }

    @Test
    @DisplayName("Should throw exception when order not found")
    void getOrderById_NotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrderById(nonExistentId))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessageContaining("Order not found with ID");
    }

    @Test
    @DisplayName("Should update order status successfully")
    void updateOrderStatus_Success() {
        // Arrange
        testOrder.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        OrderResponse response = orderService.updateOrderStatus(testOrder.getId(), OrderStatus.CONFIRMED);

        // Assert
        verify(orderRepository).save(any(Order.class));
        verify(kafkaEventPublisher).publishOrderStatusChanged(any(Order.class), eq(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("Should throw exception for invalid status transition")
    void updateOrderStatus_InvalidTransition() {
        // Arrange
        testOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> orderService.updateOrderStatus(testOrder.getId(), OrderStatus.PENDING))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid status transition");
    }

    @Test
    @DisplayName("Should cancel order successfully")
    void cancelOrder_Success() {
        // Arrange
        testOrder.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.cancelOrder(testOrder.getId());

        // Assert
        verify(orderRepository).save(any(Order.class));
        verify(kafkaEventPublisher).publishOrderCancelled(any(Order.class));
    }

    @Test
    @DisplayName("Should throw exception when cancelling already delivered order")
    void cancelOrder_CannotCancelDelivered() {
        // Arrange
        testOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancelOrder(testOrder.getId()))
            .isInstanceOf(IllegalStateException.class);
    }
}
