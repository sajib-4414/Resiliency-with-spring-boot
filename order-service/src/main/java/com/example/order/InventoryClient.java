package com.example.order;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "inventory", url = "${inventory.url}", configuration = InventoryClientConfig.class)
public interface InventoryClient {

    // Retried by Resilience4j (config: resilience4j.retry.instances.inventory in application.yml).
    @Retry(name = "inventory")
    @GetMapping("/inventory/{sku}")
    StockResponse getStock(@PathVariable("sku") String sku);

    record StockResponse(String sku, int available) {
    }
}
