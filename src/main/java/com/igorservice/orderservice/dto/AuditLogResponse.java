package com.igorservice.orderservice.dto;

import com.igorservice.orderservice.model.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for audit log entries.
 *
 * <p>Maps an {@link AuditLog} entity to its API representation so the entity
 * is never exposed directly. All timestamps serialize as ISO 8601 strings
 * (Jackson is configured with {@code write-dates-as-timestamps: false}).</p>
 */
@Schema(description = "Audit log entry for an order lifecycle event")
public record AuditLogResponse(
        @Schema(description = "Audit entry ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Order ID this entry belongs to (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID orderId,

        @Schema(description = "Event type", example = "ORDER_CANCELLED")
        String eventType,

        @Schema(description = "Full order snapshot at event time (JSON string)", example = "{\"orderId\":\"...\",\"orderNumber\":\"ORD-20260805-00001\",\"status\":\"CANCELLED\"}")
        String eventData,

        @Schema(description = "Order status before the event (null for ORDER_CREATED)", example = "PENDING")
        String previousStatus,

        @Schema(description = "Order status after the event", example = "CANCELLED")
        String newStatus,

        @Schema(description = "Actor ID (trace ID from MDC context)", example = "trace:7f0f0c2a-5f8e-4b0a-9c3d-1e2f3a4b5c6d")
        String actorId,

        @Schema(description = "When the event occurred (UTC)", example = "2026-08-05T10:30:00Z")
        Instant timestamp
) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOrderId(),
                log.getEventType(),
                log.getEventData(),
                log.getPreviousStatus(),
                log.getNewStatus(),
                log.getActorId(),
                log.getTimestamp()
        );
    }
}
