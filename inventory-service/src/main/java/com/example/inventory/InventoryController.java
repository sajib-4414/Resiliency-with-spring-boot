package com.example.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InventoryController {

    private final StockItemRepository repository;

    public InventoryController(StockItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/inventory/{sku}")
    public StockResponse get(@PathVariable String sku) {
        return repository.findById(sku)
                .map(item -> new StockResponse(item.getSku(), item.getAvailable()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown sku: " + sku));
    }

    public record StockResponse(String sku, int available) {
    }
}
