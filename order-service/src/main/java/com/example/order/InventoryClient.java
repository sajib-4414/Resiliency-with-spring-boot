package com.example.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "inventory", url = "${inventory.url}")
public interface InventoryClient {

    @GetMapping("/inventory/{sku}")
    StockResponse getStock(@PathVariable("sku") String sku);

    record StockResponse(String sku, int available) {
    }
}
