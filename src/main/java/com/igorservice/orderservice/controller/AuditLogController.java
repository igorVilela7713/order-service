package com.igorservice.orderservice.controller;

import com.igorservice.orderservice.dto.AuditLogResponse;
import com.igorservice.orderservice.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for audit log retrieval.
 *
 * <p>Exposes the query methods of {@link AuditLogService} (merged in PR #10)
 * as versioned endpoints. All endpoints require the {@code X-API-KEY} header
 * (enforced by {@code ApiKeyAuthFilter}); pagination follows the same
 * conventions as {@link OrderController} (page/size/sort/direction).</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Log", description = "Audit log retrieval operations")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/orders/{orderId}/audit")
    @Operation(summary = "Get paginated audit trail for a specific order",
        description = "Returns the audit entries (e.g. ORDER_CREATED, ORDER_STATUS_CHANGED, ORDER_CANCELLED) recorded for one order, newest first by default.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Paginated audit trail"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogForOrder(
            @Parameter(description = "Order ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        size = Math.min(size, 100); // Cap at 100
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        log.debug("GET /api/v1/orders/{}/audit - page={}, size={}", orderId, page, size);
        return ResponseEntity.ok(
                auditLogService.getAuditLogForOrder(orderId, pageable).map(AuditLogResponse::from));
    }

    @GetMapping("/audit/events")
    @Operation(summary = "Get audit logs filtered by event type",
        description = "Returns all audit entries for a given event type (e.g. ORDER_CANCELLED), paginated.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Paginated audit entries"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByEventType(
            @Parameter(description = "Event type to filter by", example = "ORDER_CANCELLED")
            @RequestParam String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        size = Math.min(size, 100);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        log.debug("GET /api/v1/audit/events - eventType={}, page={}, size={}", eventType, page, size);
        return ResponseEntity.ok(
                auditLogService.getAuditLogsByEventType(eventType, pageable).map(AuditLogResponse::from));
    }

    @GetMapping("/audit/recent")
    @Operation(summary = "Get recent audit logs",
        description = "Returns audit entries recorded since the given instant (e.g. last 24h), paginated.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Paginated audit entries"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
    public ResponseEntity<Page<AuditLogResponse>> getRecentAuditLogs(
            @Parameter(description = "Only entries at or after this instant (ISO-8601)", example = "2026-08-08T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        log.debug("GET /api/v1/audit/recent - since={}, page={}, size={}", since, page, size);
        return ResponseEntity.ok(
                auditLogService.getRecentAuditLogs(since, pageable).map(AuditLogResponse::from));
    }

    @GetMapping("/orders/{orderId}/audit/count")
    @Operation(summary = "Count audit entries for a specific order",
        responses = {
            @ApiResponse(responseCode = "200", description = "Count of audit entries")
        })
    public ResponseEntity<Map<String, Long>> countAuditLogsForOrder(
            @Parameter(description = "Order ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID orderId) {

        log.debug("GET /api/v1/orders/{}/audit/count", orderId);
        return ResponseEntity.ok(Map.of("count", auditLogService.countAuditLogsForOrder(orderId)));
    }

    @GetMapping("/audit/events/count")
    @Operation(summary = "Count audit entries by event type",
        description = "Total number of audit entries for an event type (e.g. total cancellations).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Count of audit entries")
        })
    public ResponseEntity<Map<String, Long>> countAuditLogsByEventType(
            @Parameter(description = "Event type to count", example = "ORDER_CANCELLED")
            @RequestParam String eventType) {

        log.debug("GET /api/v1/audit/events/count - eventType={}", eventType);
        return ResponseEntity.ok(Map.of("count", auditLogService.countAuditLogsByEventType(eventType)));
    }
}
