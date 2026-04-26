package com.example.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.payment.exception.BadRequestException;
import com.example.payment.exception.ConflictException;
import com.example.payment.exception.NotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        ResponseEntity<ErrorResponse> res = handler.notFound(new NotFoundException("Missing"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(res.getBody().message()).isEqualTo("Missing");
        assertThat(res.getBody().path()).isEqualTo("/test");
    }

    @Test
    void handleNotFound_includesRequestIdFromMdc() {
        MDC.put("requestId", "abc-99");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        ErrorResponse body = handler.notFound(new NotFoundException("x"), request).getBody();
        assertThat(body.requestId()).isEqualTo("abc-99");
    }

    @Test
    void handleBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        ResponseEntity<ErrorResponse> res = handler.badRequest(new BadRequestException("Invalid"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void handleConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        ResponseEntity<ErrorResponse> res = handler.conflict(new ConflictException("Double"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void handleValidation_withFieldErrors() throws Exception {
        Method m = ValidStub.class.getDeclaredMethod("post", Form.class);
        MethodParameter param = new MethodParameter(m, 0);
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Form(), "form");
        errors.addError(new FieldError("form", "name", "required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, errors);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1");
        ErrorResponse body = handler.validation(ex, request).getBody();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.message()).contains("name: required");
    }

    @Test
    void handleValidation_noFieldDetails_usesDefaultMessage() throws Exception {
        Method m = ValidStub.class.getDeclaredMethod("post", Form.class);
        MethodParameter param = new MethodParameter(m, 0);
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Form(), "form");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, errors);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/p");
        ErrorResponse body = handler.validation(ex, request).getBody();
        assertThat(body.message()).isEqualTo("Validation failed");
    }

    @Test
    void handleConstraintViolation() {
        ConstraintViolationException ex = new ConstraintViolationException("id: must not be null", Collections.emptySet());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/q");
        ErrorResponse body = handler.constraint(ex, request).getBody();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.message()).isEqualTo("id: must not be null");
    }

    @Test
    void handleCircuitOpen() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("t");
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        ResponseEntity<ErrorResponse> res = handler.circuitOpen(ex, request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().code()).isEqualTo("CIRCUIT_OPEN");
        assertThat(res.getBody().message()).isEqualTo("Upstream dependency protected by circuit breaker");
    }

    @Test
    void handleNoResource() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing.css");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing.css");
        ResponseEntity<ErrorResponse> res = handler.noResource(ex, request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(res.getBody().message()).isEqualTo("No resource for /missing.css");
    }

    @Test
    void handleMethodNotSupported_usesExceptionMessage() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/");
        String msg = ex.getMessage();
        assertThat(msg).isNotNull();
        ResponseEntity<ErrorResponse> res = handler.methodNotSupported(ex, request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(res.getBody().message()).isEqualTo(msg);
    }

    @Test
    void handleMethodNotSupported_fallsBackWhenMessageNull() {
        HttpRequestMethodNotSupportedException ex = mock(HttpRequestMethodNotSupportedException.class);
        when(ex.getMessage()).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/r");
        ErrorResponse body = handler.methodNotSupported(ex, request).getBody();
        assertThat(body.message()).isEqualTo("HTTP method not supported");
    }

    @Test
    void handleGeneric() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        ResponseEntity<ErrorResponse> res = handler.generic(new RuntimeException("Oops"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(res.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    private static class Form {
        @SuppressWarnings("unused")
        private String name;
    }

    @SuppressWarnings("unused")
    private static class ValidStub {
        void post(Form form) {}
    }
}
