package com.example.order;

import com.example.order.InventoryExceptions.OutOfStockException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrderController {

    private final InventoryGateway inventory;
    private final OrderRepository orders;

    public OrderController(InventoryGateway inventory, OrderRepository orders) {
        this.inventory = inventory;
        this.orders = orders;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody CreateOrderRequest request) {
        // Synchronous call: we need the answer before we can accept the order.
        InventoryClient.StockResponse stock = inventory.getStock(request.sku());
        if (stock.available() < request.qty()) {
            throw new OutOfStockException(
                    "Only " + stock.available() + " of " + request.sku() + " available, requested " + request.qty());
        }
        return orders.save(new Order(request.sku(), request.qty(), "CREATED"));
    }

    @GetMapping("/orders/{id}")
    public Order get(@PathVariable Long id) {
        return orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    public record CreateOrderRequest(@NotBlank String sku, @NotNull @Min(1) Integer qty) {
    }
}
