package com.igorservice.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igorservice.orderservice.dto.OrderRequest;
import com.igorservice.orderservice.dto.OrderResponse;
import com.igorservice.orderservice.exception.GlobalExceptionHandler;
import com.igorservice.orderservice.exception.OrderNotFoundException;
import com.igorservice.orderservice.model.OrderStatus;
import com.igorservice.orderservice.service.OrderSearchService;
import com.igorservice.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderSearchService orderSearchService;

    private OrderRequest validRequest;
    private OrderResponse sampleResponse;

    @BeforeEach
    void setUp() {
        validRequest = new OrderRequest();
        validRequest.setCustomerId("customer-001");
        validRequest.setItems(List.of(
            new OrderRequest.OrderItemRequest("PROD-001", "Widget Pro", 2, new BigDecimal("29.99"))
        ));

        sampleResponse = OrderResponse.builder()
            .id(UUID.randomUUID())
            .orderNumber("ORD-20260805-00001")
            .customerId("customer-001")
            .status("PENDING")
            .totalAmount(new BigDecimal("59.98"))
            .items(List.of())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 201 on valid request")
    void createOrder_Success() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-KEY", "test-key")
                .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderNumber").value("ORD-20260805-00001"))
            .andExpect(jsonPath("$.customerId").value("customer-001"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 422 when customer ID is blank")
    void createOrder_InvalidRequest_MissingCustomerId() throws Exception {
        validRequest.setCustomerId("");

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-KEY", "test-key")
                .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should return order")
    void getOrderById_Success() throws Exception {
        when(orderService.getOrderById(sampleResponse.getId())).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/orders/" + sampleResponse.getId())
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sampleResponse.getId().toString()));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should return 404 when not found")
    void getOrderById_NotFound() throws Exception {
        UUID notFoundId = UUID.randomUUID();
        when(orderService.getOrderById(notFoundId))
            .thenThrow(new OrderNotFoundException("Order not found with ID: " + notFoundId));

        mockMvc.perform(get("/api/v1/orders/" + notFoundId)
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Order not found with ID: " + notFoundId));
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} should return 204 on success")
    void cancelOrder_Success() throws Exception {
        doNothing().when(orderService).cancelOrder(sampleResponse.getId());

        mockMvc.perform(delete("/api/v1/orders/" + sampleResponse.getId())
                .header("X-API-KEY", "test-key"))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{id}/status should return 200")
    void updateOrderStatus_Success() throws Exception {
        sampleResponse.setStatus("CONFIRMED");
        when(orderService.updateOrderStatus(sampleResponse.getId(), OrderStatus.CONFIRMED))
            .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/orders/" + sampleResponse.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-KEY", "test-key")
                .content("{\"status\":\"CONFIRMED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
