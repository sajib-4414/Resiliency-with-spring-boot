package com.example.order;

import com.example.order.InventoryExceptions.InventoryRateLimitedException;
import com.example.order.InventoryExceptions.InventoryUnavailableException;
import com.example.order.InventoryExceptions.OutOfStockException;
import com.example.order.InventoryExceptions.UnknownSkuException;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UnknownSkuException.class)
    ResponseEntity<Map<String, String>> unknownSku(UnknownSkuException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<Map<String, String>> orderNotFound(OrderNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** A missing/null field is a malformed request (400); a present value that breaks a rule is 422. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidFields(MethodArgumentNotValidException e) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors();
        boolean missing = errors.stream().anyMatch(f -> f.getRejectedValue() == null);
        String message = errors.stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(missing ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    /** Body that cannot be parsed at all (bad JSON, wrong type, missing body): 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(OutOfStockException.class)
    ResponseEntity<Map<String, String>> outOfStock(OutOfStockException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(InventoryUnavailableException e) {
        log.warn("Inventory unavailable: {}", e.toString());
        return body(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable, try later");
    }

    /** Feign wraps I/O failures; a timeout is 504, anything else (e.g. connection refused) is 503. */
    @ExceptionHandler(RetryableException.class)
    ResponseEntity<Map<String, String>> ioFailure(RetryableException e) {
        log.warn("Inventory call failed: {}", e.toString());
        if (e.getCause() instanceof SocketTimeoutException) {
            return body(HttpStatus.GATEWAY_TIMEOUT, "Inventory did not respond in time");
        }
        return body(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable, try later");
    }

    @ExceptionHandler(InventoryRateLimitedException.class)
    ResponseEntity<Map<String, String>> rateLimited(InventoryRateLimitedException e) {
        return body(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", String.valueOf(status.value()), "error", message));
    }
}
