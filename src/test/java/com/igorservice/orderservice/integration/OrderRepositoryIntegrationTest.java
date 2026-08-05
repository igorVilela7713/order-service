package com.igorservice.orderservice.integration;

import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderItem;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("orders_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save order and find by order number")
    void saveAndFindByOrderNumber() {
        // Arrange
        Order order = createTestOrder("ORD-20260805-00001", "customer-001", OrderStatus.PENDING);

        // Act
        Order saved = orderRepository.save(order);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrderNumber()).isEqualTo("ORD-20260805-00001");

        var found = orderRepository.findByOrderNumber("ORD-20260805-00001");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getCustomerId()).isEqualTo("customer-001");
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should find orders by customer ID with pagination")
    void findByCustomerIdWithPagination() {
        // Arrange — create 5 orders for same customer, 3 for another
        for (int i = 1; i <= 5; i++) {
            orderRepository.save(createTestOrder(
                "ORD-20260805-0000" + i, "customer-001", OrderStatus.PENDING));
        }
        for (int i = 1; i <= 3; i++) {
            orderRepository.save(createTestOrder(
                "ORD-20260805-0001" + i, "customer-002", OrderStatus.CONFIRMED));
        }

        // Act — page 1, size 3 for customer-001
        Page<Order> page1 = orderRepository.findByCustomerId("customer-001", PageRequest.of(0, 3));
        Page<Order> page2 = orderRepository.findByCustomerId("customer-001", PageRequest.of(1, 3));

        // Assert
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getContent()).hasSize(3);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        assertThat(page2.getContent()).hasSize(2);
        assertThat(page2.getNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should count orders by status")
    void countByStatus() {
        // Arrange
        orderRepository.save(createTestOrder("ORD-20260805-00001", "customer-001", OrderStatus.PENDING));
        orderRepository.save(createTestOrder("ORD-20260805-00002", "customer-002", OrderStatus.PENDING));
        orderRepository.save(createTestOrder("ORD-20260805-00003", "customer-003", OrderStatus.CONFIRMED));
        orderRepository.save(createTestOrder("ORD-20260805-00004", "customer-004", OrderStatus.DELIVERED));

        // Act
        long pendingCount = orderRepository.countByStatus(OrderStatus.PENDING);
        long confirmedCount = orderRepository.countByStatus(OrderStatus.CONFIRMED);
        long deliveredCount = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledCount = orderRepository.countByStatus(OrderStatus.CANCELLED);

        // Assert
        assertThat(pendingCount).isEqualTo(2);
        assertThat(confirmedCount).isEqualTo(1);
        assertThat(deliveredCount).isEqualTo(1);
        assertThat(cancelledCount).isZero();
    }

    private Order createTestOrder(String orderNumber, String customerId, OrderStatus status) {
        Order order = Order.builder()
            .orderNumber(orderNumber)
            .customerId(customerId)
            .status(status)
            .totalAmount(new BigDecimal("99.99"))
            .build();

        order.addItem(OrderItem.builder()
            .productId("PROD-001")
            .productName("Test Product")
            .quantity(1)
            .unitPrice(new BigDecimal("99.99"))
            .build());

        order.recalculateTotal();
        return order;
    }
}
