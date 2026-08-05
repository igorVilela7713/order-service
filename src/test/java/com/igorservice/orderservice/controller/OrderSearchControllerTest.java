package com.igorservice.orderservice.controller;

import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.exception.GlobalExceptionHandler;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.service.OrderSearchService;
import com.igorservice.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderController — search endpoint")
class OrderSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderSearchService orderSearchService;

    private OrderResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = OrderResponse.builder()
            .id(UUID.randomUUID())
            .orderNumber("ORD-20260805-00001")
            .customerId("customer-001")
            .status("PENDING")
            .totalAmount(new BigDecimal("59.98"))
            .items(List.of())
            .createdAt(Instant.parse("2026-08-05T10:00:00Z"))
            .updatedAt(Instant.parse("2026-08-05T10:00:00Z"))
            .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders/search should return 200 with results")
    void searchOrders_noFilters() throws Exception {
        // Arrange
        Page<OrderResponse> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 20), 1);
        when(orderSearchService.search(eq(null), eq(null), eq(null), eq(null), any(PageRequest.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/search")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-20260805-00001"))
            .andExpect(jsonPath("$.content[0].customerId").value("customer-001"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/search with status filter should pass status to service")
    void searchOrders_withStatusFilter() throws Exception {
        // Arrange
        Page<OrderResponse> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 20), 1);
        when(orderSearchService.search(eq(null), eq(null), eq(OrderStatus.PENDING), eq(null), any(PageRequest.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/search")
                .param("status", "PENDING")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/search with customerId filter should pass customerId to service")
    void searchOrders_withCustomerIdFilter() throws Exception {
        // Arrange
        Page<OrderResponse> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 20), 1);
        when(orderSearchService.search(eq(null), eq(null), eq(null), eq("customer-001"), any(PageRequest.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/search")
                .param("customerId", "customer-001")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].customerId").value("customer-001"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/search with date range should pass dates to service")
    void searchOrders_withDateRange() throws Exception {
        // Arrange
        Page<OrderResponse> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 20), 1);
        String startDate = "2026-08-01T00:00:00Z";
        String endDate = "2026-08-06T00:00:00Z";
        when(orderSearchService.search(any(Instant.class), any(Instant.class), eq(null), eq(null), any(PageRequest.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/search")
                .param("startDate", startDate)
                .param("endDate", endDate)
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-20260805-00001"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/search with pagination parameters should respect page and size")
    void searchOrders_withPagination() throws Exception {
        // Arrange
        Page<OrderResponse> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(1, 10), 20);
        when(orderSearchService.search(eq(null), eq(null), eq(null), eq(null), any(PageRequest.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/search")
                .param("page", "1")
                .param("size", "10")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(20))
            .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/orders/search with all filters combined")
    void searchOrders_allFiltersCombined() throws Exception {
        // Arrange
        Page<OrderResponse> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 20), 1);
        String startDate = "2026-08-01T00:00:00Z";
        String endDate = "2026-08-06T00:00:00Z";
        when(orderSearchService.search(any(Instant.class), any(Instant.class), eq(OrderStatus.PENDING), eq("customer-001"), any(PageRequest.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/search")
                .param("startDate", startDate)
                .param("endDate", endDate)
                .param("status", "PENDING")
                .param("customerId", "customer-001")
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].customerId").value("customer-001"));
    }
}
