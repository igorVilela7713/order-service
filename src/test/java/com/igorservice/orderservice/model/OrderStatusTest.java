package com.igorservice.orderservice.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus — state machine validation")
class OrderStatusTest {

    // --- Valid transitions ---

    @Test
    @DisplayName("PENDING can transition to CONFIRMED")
    void pending_canTransitionTo_confirmed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    @DisplayName("PENDING can transition to CANCELLED")
    void pending_canTransitionTo_cancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("CONFIRMED can transition to PROCESSING")
    void confirmed_canTransitionTo_processing() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
    }

    @Test
    @DisplayName("CONFIRMED can transition to CANCELLED")
    void confirmed_canTransitionTo_cancelled() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("PROCESSING can transition to SHIPPED")
    void processing_canTransitionTo_shipped() {
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    @DisplayName("PROCESSING can transition to CANCELLED")
    void processing_canTransitionTo_cancelled() {
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("SHIPPED can transition to DELIVERED")
    void shipped_canTransitionTo_delivered() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    // --- Invalid transitions ---

    @Test
    @DisplayName("PENDING cannot transition to SHIPPED")
    void pending_cannotTransitionTo_shipped() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    @DisplayName("PENDING cannot transition to PROCESSING")
    void pending_cannotTransitionTo_processing() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PROCESSING)).isFalse();
    }

    @Test
    @DisplayName("PENDING cannot transition to DELIVERED")
    void pending_cannotTransitionTo_delivered() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED cannot transition to SHIPPED")
    void confirmed_cannotTransitionTo_shipped() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED cannot transition to DELIVERED")
    void confirmed_cannotTransitionTo_delivered() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("PROCESSING cannot transition to DELIVERED")
    void processing_cannotTransitionTo_delivered() {
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("SHIPPED cannot transition to CONFIRMED")
    void shipped_cannotTransitionTo_confirmed() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    @DisplayName("SHIPPED cannot transition to CANCELLED")
    void shipped_cannotTransitionTo_cancelled() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    // --- Terminal states ---

    @Test
    @DisplayName("DELIVERED is terminal — cannot transition to any status")
    void delivered_isTerminal() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target))
                .as("DELIVERED -> %s should be false", target)
                .isFalse();
        }
    }

    @Test
    @DisplayName("CANCELLED is terminal — cannot transition to any status")
    void cancelled_isTerminal() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target))
                .as("CANCELLED -> %s should be false", target)
                .isFalse();
        }
    }
}
