package com.example.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void constructorSetsFieldsAndTimestamp() {
        Instant before = Instant.now();

        Order order = new Order("book", 2, "CREATED");

        assertThat(order.getId()).isNull();
        assertThat(order.getSku()).isEqualTo("book");
        assertThat(order.getQty()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo("CREATED");
        assertThat(order.getCreatedAt()).isBetween(before, Instant.now());
    }
}
