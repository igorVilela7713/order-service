package com.igorservice.orderservice.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("OrderItem — total price calculation")
class OrderItemTest {

    @Test
    @DisplayName("calculateTotalPrice should multiply quantity by unit price")
    void calculateTotalPrice_validValues() {
        // Arrange
        OrderItem item = OrderItem.builder()
            .productId("PROD-001")
            .productName("Widget")
            .quantity(3)
            .unitPrice(new BigDecimal("25.00"))
            .build();

        // Act
        item.calculateTotalPrice();

        // Assert
        assertThat(item.getTotalPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("calculateTotalPrice with quantity=1 should equal unit price")
    void calculateTotalPrice_singleQuantity() {
        // Arrange
        OrderItem item = OrderItem.builder()
            .productId("PROD-002")
            .productName("Single Item")
            .quantity(1)
            .unitPrice(new BigDecimal("49.99"))
            .build();

        // Act
        item.calculateTotalPrice();

        // Assert
        assertThat(item.getTotalPrice()).isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    @DisplayName("calculateTotalPrice should not throw when quantity is null")
    void calculateTotalPrice_nullQuantity() {
        // Arrange
        OrderItem item = OrderItem.builder()
            .productId("PROD-003")
            .productName("Null Qty Item")
            .quantity(null)
            .unitPrice(new BigDecimal("10.00"))
            .build();

        // Act & Assert
        assertThatCode(item::calculateTotalPrice).doesNotThrowAnyException();
        assertThat(item.getTotalPrice()).isNull();
    }

    @Test
    @DisplayName("calculateTotalPrice should not throw when unitPrice is null")
    void calculateTotalPrice_nullUnitPrice() {
        // Arrange
        OrderItem item = OrderItem.builder()
            .productId("PROD-004")
            .productName("Null Price Item")
            .quantity(5)
            .unitPrice(null)
            .build();

        // Act & Assert
        assertThatCode(item::calculateTotalPrice).doesNotThrowAnyException();
        assertThat(item.getTotalPrice()).isNull();
    }

    @Test
    @DisplayName("calculateTotalPrice should handle large quantities and prices")
    void calculateTotalPrice_largeValues() {
        // Arrange
        OrderItem item = OrderItem.builder()
            .productId("PROD-005")
            .productName("Bulk Item")
            .quantity(1000)
            .unitPrice(new BigDecimal("999.99"))
            .build();

        // Act
        item.calculateTotalPrice();

        // Assert: 1000 * 999.99 = 999990.00
        assertThat(item.getTotalPrice()).isEqualByComparingTo(new BigDecimal("999990.00"));
    }
}
