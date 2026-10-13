package com.example.order;

import com.example.order.InventoryExceptions.OutOfStockException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final InventoryClient inventory;
    private final OrderRepository orders;

    public OrderService(InventoryClient inventory, OrderRepository orders) {
        this.inventory = inventory;
        this.orders = orders;
    }

    public Order create(String sku, int qty) {
        // Synchronous call: we need the answer before we can accept the order.
        InventoryClient.StockResponse stock = inventory.getStock(sku);
        if (stock.available() < qty) {
            throw new OutOfStockException(
                    "Only " + stock.available() + " of " + sku + " available, requested " + qty);
        }
        return orders.save(new Order(sku, qty, "CREATED"));
    }

    public Order get(Long id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException("Order not found"));
    }
}
