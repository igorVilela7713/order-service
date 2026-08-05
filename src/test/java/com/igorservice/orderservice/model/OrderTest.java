package com.igorservice.orderservice.model;

import com.igorservice.orderservice.builder.OrderTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Order entity — bidirectional relationships and total recalculation")
class OrderTest {

    @Test
    @DisplayName("addItem should set bidirectional relationship")
    void addItem_setsBidirectionalRelationship() {
        // Arrange
        Order order = OrderTestDataBuilder.anOrder().build();
        OrderItem item = OrderTestDataBuilder.buildOrderItem("PROD-001", "Widget", 1, new BigDecimal("10.00"));

        // Act
        order.addItem(item);

        // Assert
        assertThat(order.getItems()).contains(item);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    @DisplayName("removeItem should clear bidirectional relationship")
    void removeItem_clearsBidirectionalRelationship() {
        // Arrange
        Order order = OrderTestDataBuilder.anOrder().build();
        OrderItem item = OrderTestDataBuilder.buildOrderItem("PROD-001", "Widget", 1, new BigDecimal("10.00"));
        order.addItem(item);

        // Act
        order.removeItem(item);

        // Assert
        assertThat(order.getItems()).doesNotContain(item);
        assertThat(item.getOrder()).isNull();
    }

    @Test
    @DisplayName("recalculateTotal should sum all item total prices")
    void recalculateTotal_multipleItems() {
        // Arrange
        Order order = OrderTestDataBuilder.anOrder()
            .withTotalAmount(BigDecimal.ZERO)
            .build();
        order.addItem(OrderTestDataBuilder.buildOrderItem("PROD-001", "Widget A", 2, new BigDecimal("25.00")));
        order.addItem(OrderTestDataBuilder.buildOrderItem("PROD-002", "Widget B", 1, new BigDecimal("49.99")));

        // Act
        order.recalculateTotal();

        // Assert: 2 * 25.00 + 1 * 49.99 = 99.99
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
    }

    @Test
    @DisplayName("recalculateTotal with empty items should set total to ZERO")
    void recalculateTotal_emptyItems() {
        // Arrange
        Order order = OrderTestDataBuilder.anOrder()
            .withTotalAmount(new BigDecimal("100.00"))
            .build();

        // Act
        order.recalculateTotal();

        // Assert
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("recalculateTotal with single item should use item total")
    void recalculateTotal_singleItem() {
        // Arrange
        Order order = OrderTestDataBuilder.anOrder()
            .withTotalAmount(BigDecimal.ZERO)
            .build();
        order.addItem(OrderTestDataBuilder.buildOrderItem("PROD-001", "Widget", 3, new BigDecimal("15.50")));

        // Act
        order.recalculateTotal();

        // Assert: 3 * 15.50 = 46.50
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("46.50"));
    }
}
