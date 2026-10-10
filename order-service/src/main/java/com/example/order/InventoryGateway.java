package com.example.order;

import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the Feign client. Resilience4j annotations (retry, circuit breaker,
 * rate limiter, bulkhead) are added here step by step.
 */
@Component
public class InventoryGateway {

    private final InventoryClient client;

    public InventoryGateway(InventoryClient client) {
        this.client = client;
    }

    public InventoryClient.StockResponse getStock(String sku) {
        return client.getStock(sku);
    }
}
