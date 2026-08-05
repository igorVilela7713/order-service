package com.igorservice.orderservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler — error response mapping")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    private ResponseEntity<Map<String, Object>> response;

    @Test
    @DisplayName("handleOrderNotFound should return 404 with message")
    void handleOrderNotFound_returns404() {
        // Arrange
        OrderNotFoundException ex = new OrderNotFoundException("Order not found with ID: abc-123");

        // Act
        response = handler.handleOrderNotFound(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(404);
        assertThat(response.getBody().get("error")).isEqualTo("Not Found");
        assertThat(response.getBody().get("message")).isEqualTo("Order not found with ID: abc-123");
    }

    @Test
    @DisplayName("handleIllegalState should return 409 with message")
    void handleIllegalState_returns409() {
        // Arrange
        IllegalStateException ex = new IllegalStateException("Invalid status transition from PENDING to SHIPPED");

        // Act
        response = handler.handleIllegalState(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(409);
        assertThat(response.getBody().get("error")).isEqualTo("Conflict");
        assertThat(response.getBody().get("message")).isEqualTo("Invalid status transition from PENDING to SHIPPED");
    }

    @Test
    @DisplayName("handleValidationErrors should return 422 with field errors")
    void handleValidationErrors_returns422() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError customerIdError = new FieldError("orderRequest", "customerId", "Customer ID is required");
        FieldError itemsError = new FieldError("orderRequest", "items", "Order must contain at least one item");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(customerIdError, itemsError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
            (MethodParameter) null, bindingResult);

        // Act
        response = handler.handleValidationErrors(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(422);
        assertThat(response.getBody().get("error")).isEqualTo("Validation Failed");

        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("customerId", "Customer ID is required");
        assertThat(fieldErrors).containsEntry("items", "Order must contain at least one item");
    }

    @Test
    @DisplayName("handleGeneric should return 500 with generic message")
    void handleGeneric_returns500() {
        // Arrange
        Exception ex = new RuntimeException("Something unexpected happened");

        // Act
        response = handler.handleGeneric(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(500);
        assertThat(response.getBody().get("error")).isEqualTo("Internal Server Error");
        assertThat(response.getBody().get("message")).isEqualTo("An unexpected error occurred");
    }
}
