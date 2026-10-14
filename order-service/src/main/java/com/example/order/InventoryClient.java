package com.example.order;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "inventory", url = "${inventory.url}", configuration = InventoryClientConfig.class)
public interface InventoryClient {

    // Resilience4j aspect order: Retry wraps CircuitBreaker, so every attempt is counted by the breaker,
    // and an open circuit (CallNotPermittedException) is not retried. Bulkhead is innermost: each attempt
    // takes a permit, and a full bulkhead (BulkheadFullException) is neither retried nor counted by the breaker.
    // Config: application.yml.
    @Retry(name = "inventory")
    @CircuitBreaker(name = "inventory")
    @Bulkhead(name = "inventory")
    @GetMapping("/inventory/{sku}")
    StockResponse getStock(@PathVariable("sku") String sku);

    record StockResponse(String sku, int available) {
    }
}
