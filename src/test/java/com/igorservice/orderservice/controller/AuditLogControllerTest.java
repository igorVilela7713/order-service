package com.igorservice.orderservice.controller;

import com.igorservice.orderservice.exception.GlobalExceptionHandler;
import com.igorservice.orderservice.model.AuditLog;
import com.igorservice.orderservice.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    private UUID orderId;
    private AuditLog sampleAuditLog;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        sampleAuditLog = AuditLog.builder()
            .id(UUID.randomUUID())
            .orderId(orderId)
            .eventType("ORDER_CANCELLED")
            .eventData("{\"orderId\":\"" + orderId + "\",\"orderNumber\":\"ORD-20260805-00001\",\"status\":\"CANCELLED\"}")
            .previousStatus("PENDING")
            .newStatus("CANCELLED")
            .actorId("trace:7f0f0c2a-5f8e-4b0a-9c3d-1e2f3a4b5c6d")
            .timestamp(Instant.parse("2026-08-05T10:30:00Z"))
            .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}/audit should return paginated audit trail")
    void getAuditLogForOrder_Success() throws Exception {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
        when(auditLogService.getAuditLogForOrder(eq(orderId), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/audit")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.content[0].eventType").value("ORDER_CANCELLED"))
            .andExpect(jsonPath("$.content[0].previousStatus").value("PENDING"))
            .andExpect(jsonPath("$.content[0].newStatus").value("CANCELLED"))
            .andExpect(jsonPath("$.content[0].actorId").value("trace:7f0f0c2a-5f8e-4b0a-9c3d-1e2f3a4b5c6d"))
            .andExpect(jsonPath("$.content[0].timestamp").value("2026-08-05T10:30:00Z"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}/audit should return empty page when no audit entries")
    void getAuditLogForOrder_Empty() throws Exception {
        Page<AuditLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(auditLogService.getAuditLogForOrder(eq(orderId), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/audit")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/audit/events should filter by event type")
    void getAuditLogsByEventType_Success() throws Exception {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
        when(auditLogService.getAuditLogsByEventType(eq("ORDER_CANCELLED"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/events")
                .param("eventType", "ORDER_CANCELLED")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].eventType").value("ORDER_CANCELLED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/audit/recent should return entries since instant")
    void getRecentAuditLogs_Success() throws Exception {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
        when(auditLogService.getRecentAuditLogs(eq(Instant.parse("2026-08-08T00:00:00Z")), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/recent")
                .param("since", "2026-08-08T00:00:00Z")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].eventType").value("ORDER_CANCELLED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}/audit/count should return count")
    void countAuditLogsForOrder_Success() throws Exception {
        when(auditLogService.countAuditLogsForOrder(orderId)).thenReturn(7L);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/audit/count")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(7));
    }

    @Test
    @DisplayName("GET /api/v1/audit/events/count should return count by event type")
    void countAuditLogsByEventType_Success() throws Exception {
        when(auditLogService.countAuditLogsByEventType("ORDER_CANCELLED")).thenReturn(3L);

        mockMvc.perform(get("/api/v1/audit/events/count")
                .param("eventType", "ORDER_CANCELLED")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(3));
    }
}
