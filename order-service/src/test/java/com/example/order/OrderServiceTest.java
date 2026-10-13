package com.example.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.order.InventoryExceptions.OutOfStockException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit tests for the business logic: no Spring, both collaborators mocked. */
class OrderServiceTest {

    private final InventoryClient inventory = mock(InventoryClient.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderService service = new OrderService(inventory, orders);

    @Test
    void savesOrderWhenStockCoversQuantity() {
        when(inventory.getStock("book")).thenReturn(new InventoryClient.StockResponse("book", 3));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = service.create("book", 3);

        assertEquals("book", order.getSku());
        assertEquals("CREATED", order.getStatus());
    }

    @Test
    void rejectsAndSavesNothingWhenOutOfStock() {
        when(inventory.getStock("pen")).thenReturn(new InventoryClient.StockResponse("pen", 1));

        assertThrows(OutOfStockException.class, () -> service.create("pen", 2));
        verify(orders, never()).save(any());
    }

    @Test
    void throwsWhenOrderNotFound() {
        when(orders.findById(9L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> service.get(9L));
    }
}
