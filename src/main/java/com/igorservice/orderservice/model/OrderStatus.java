package com.igorservice.orderservice.model;

/**
 * Order lifecycle states.
 *
 * State transitions:
 *   PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
 *                    ↘ CANCELLED
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * Returns true if the transition from this status to the given target is valid.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED -> false;
            case CANCELLED -> false;
        };
    }
}
