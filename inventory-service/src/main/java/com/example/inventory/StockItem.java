package com.example.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class StockItem {

    @Id
    private String sku;
    private int available;

    protected StockItem() {
    }

    public StockItem(String sku, int available) {
        this.sku = sku;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }
}
