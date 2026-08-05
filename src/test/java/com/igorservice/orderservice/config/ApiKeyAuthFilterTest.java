package com.igorservice.orderservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyAuthFilter — API key validation")
class ApiKeyAuthFilterTest {

    private static final String VALID_API_KEY = "test-secret-key";
    private static final String API_KEY_HEADER = "X-API-KEY";

    private ApiKeyAuthFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(VALID_API_KEY);
    }

    @Test
    @DisplayName("Valid API key should pass through the filter")
    void doFilterInternal_validApiKey_passesThrough() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(API_KEY_HEADER)).thenReturn(VALID_API_KEY);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Missing API key should return 401")
    void doFilterInternal_missingApiKey_returns401() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(API_KEY_HEADER)).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Invalid API key should return 401")
    void doFilterInternal_invalidApiKey_returns401() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(API_KEY_HEADER)).thenReturn("wrong-key");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Actuator path should be skipped (shouldNotFilter returns true)")
    void shouldNotFilter_actuatorPath_returnsTrue() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/actuator/health");

        // Act & Assert
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Swagger UI path should be skipped")
    void shouldNotFilter_swaggerPath_returnsTrue() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");

        // Act & Assert
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("API docs path should be skipped")
    void shouldNotFilter_apiDocsPath_returnsTrue() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/v3/api-docs");

        // Act & Assert
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Regular API path should not be skipped")
    void shouldNotFilter_regularPath_returnsFalse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders");

        // Act & Assert
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
