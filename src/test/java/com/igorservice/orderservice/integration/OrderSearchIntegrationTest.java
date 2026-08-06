package com.igorservice.orderservice.integration;

import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.model.Order;
import com.igorservice.orderservice.model.OrderItem;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.repository.OrderRepository;
import com.igorservice.orderservice.service.OrderSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("OrderSearchService — integration tests with PostgreSQL")
class OrderSearchIntegrationTest {

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

    @Autowired
    private OrderSearchService orderSearchService;

    private final PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should search orders by date range")
    void search_byDateRange() {
        // Arrange
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        Order order1 = createTestOrder("ORD-001", "customer-001", OrderStatus.PENDING);
        order1.setCreatedAt(oneDayAgo);
        orderRepository.save(order1);

        Order order2 = createTestOrder("ORD-002", "customer-001", OrderStatus.CONFIRMED);
        order2.setCreatedAt(twoDaysAgo);
        orderRepository.save(order2);

        // Act — search within last day (should only find order1)
        Page<OrderResponse> results = orderSearchService.search(
            oneDayAgo.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), null, null, pageable);

        // Assert
        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getOrderNumber()).isEqualTo("ORD-001");
    }

    @Test
    @DisplayName("Should search orders by date range with explicit createdAt override")
    void search_byDateRange_ExplicitCreatedAt() {
        // Arrange
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        Order order1 = createTestOrder("ORD-001", "customer-001", OrderStatus.PENDING);
        orderRepository.save(order1);
        // Override createdAt via native query (createdAt is @CreationTimestamp + updatable=false)
        orderRepository.updateCreatedAt(order1.getId(), oneDayAgo);

        Order order2 = createTestOrder("ORD-002", "customer-001", OrderStatus.CONFIRMED);
        orderRepository.save(order2);
        orderRepository.updateCreatedAt(order2.getId(), twoDaysAgo);

        // Act — search within last day (should only find order1)
        Page<OrderResponse> results = orderSearchService.search(
            oneDayAgo.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), null, null, pageable);

        // Assert
        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getOrderNumber()).isEqualTo("ORD-001");
    }

    @Test
    @DisplayName("Should search orders by status")
    void search_byStatus() {
        // Arrange
        orderRepository.save(createTestOrder("ORD-001", "customer-001", OrderStatus.PENDING));
        orderRepository.save(createTestOrder("ORD-002", "customer-001", OrderStatus.CONFIRMED));
        orderRepository.save(createTestOrder("ORD-003", "customer-002", OrderStatus.PENDING));

        // Act
        Page<OrderResponse> results = orderSearchService.search(null, null, OrderStatus.PENDING, null, pageable);

        // Assert
        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent()).allMatch(order -> "PENDING".equals(order.getStatus()));
    }

    @Test
    @DisplayName("Should search orders by customerId")
    void search_byCustomerId() {
        // Arrange
        orderRepository.save(createTestOrder("ORD-001", "customer-001", OrderStatus.PENDING));
        orderRepository.save(createTestOrder("ORD-002", "customer-001", OrderStatus.CONFIRMED));
        orderRepository.save(createTestOrder("ORD-003", "customer-002", OrderStatus.PENDING));

        // Act
        Page<OrderResponse> results = orderSearchService.search(null, null, null, "customer-001", pageable);

        // Assert
        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent()).allMatch(order -> "customer-001".equals(order.getCustomerId()));
    }

    @Test
    @DisplayName("Should search with combined filters (status + customerId)")
    void search_combinedFilters() {
        // Arrange
        orderRepository.save(createTestOrder("ORD-001", "customer-001", OrderStatus.PENDING));
        orderRepository.save(createTestOrder("ORD-002", "customer-001", OrderStatus.CONFIRMED));
        orderRepository.save(createTestOrder("ORD-003", "customer-002", OrderStatus.PENDING));
        orderRepository.save(createTestOrder("ORD-004", "customer-002", OrderStatus.CONFIRMED));

        // Act — PENDING orders for customer-001
        Page<OrderResponse> results = orderSearchService.search(
            null, null, OrderStatus.PENDING, "customer-001", pageable);

        // Assert
        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getOrderNumber()).isEqualTo("ORD-001");
    }

    @Test
    @DisplayName("Should return empty page when no orders match")
    void search_noMatches() {
        // Arrange
        orderRepository.save(createTestOrder("ORD-001", "customer-001", OrderStatus.PENDING));

        // Act
        Page<OrderResponse> results = orderSearchService.search(
            null, null, OrderStatus.DELIVERED, "nonexistent-customer", pageable);

        // Assert
        assertThat(results.getTotalElements()).isZero();
        assertThat(results.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Should search with all filters combined (date range + status + customerId)")
    void search_allFiltersCombined() {
        // Arrange
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        Order order1 = createTestOrder("ORD-001", "customer-001", OrderStatus.PENDING);
        order1.setCreatedAt(now);
        orderRepository.save(order1);

        Order order2 = createTestOrder("ORD-002", "customer-001", OrderStatus.CONFIRMED);
        order2.setCreatedAt(now);
        orderRepository.save(order2);

        Order order3 = createTestOrder("ORD-003", "customer-002", OrderStatus.PENDING);
        order3.setCreatedAt(now);
        orderRepository.save(order3);

        // Act — PENDING orders for customer-001 in last day
        Page<OrderResponse> results = orderSearchService.search(
            oneDayAgo, now.plus(1, ChronoUnit.HOURS), OrderStatus.PENDING, "customer-001", pageable);

        // Assert
        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getOrderNumber()).isEqualTo("ORD-001");
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
