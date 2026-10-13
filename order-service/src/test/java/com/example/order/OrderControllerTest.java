package com.example.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.order.InventoryExceptions.InventoryRateLimitedException;
import com.example.order.InventoryExceptions.InventoryUnavailableException;
import com.example.order.InventoryExceptions.UnknownSkuException;
import feign.Request;
import feign.RetryableException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Web-layer tests: controller + exception handler, with inventory and the DB mocked out.
 *
 * @WebMvcTest
 *   Starts only the web layer (controllers, exception handler, JSON). No database, no real port.
 *
 * MockMvc
 *   Sends fake HTTP requests straight into the controller and asserts on the response,
 *   e.g. status().isCreated(), jsonPath("$.sku").value("book").
 *
 * @MockitoBean
 *   Puts a fake (Mockito mock) in place of a real bean. The web layer alone has no
 *   InventoryClient or OrderRepository, so we supply mocks. A mock does nothing until told to.
 *
 * Telling a mock what to do
 *   when(inventory.getStock("book")).thenReturn(x)   -> the call returns x
 *   when(...).thenThrow(e)                           -> the call throws e (simulates inventory failing)
 *   when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0))
 *                                                    -> returns whatever argument it received
 *                                                       (a fake save that hands back the same order)
 *
 * Checking what happened
 *   verify(orders, never()).save(any())  -> assert save() was NOT called (e.g. out of stock)
 *   any()                                -> matches any argument
 */
@WebMvcTest({OrderController.class, ApiExceptionHandler.class})
@Import(OrderService.class)
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryClient inventory;

    @MockitoBean
    OrderRepository orders;

    @Test
    void createsOrderWhenStockIsAvailable() throws Exception {
        when(inventory.getStock("book")).thenReturn(new InventoryClient.StockResponse("book", 10));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(postOrder("{\"sku\":\"book\",\"qty\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("book"))
                .andExpect(jsonPath("$.qty").value(2))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void acceptsOrderForExactlyAvailableStock() throws Exception {
        when(inventory.getStock("laptop")).thenReturn(new InventoryClient.StockResponse("laptop", 3));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(postOrder("{\"sku\":\"laptop\",\"qty\":3}")).andExpect(status().isCreated());
    }

    @Test
    void returns422AndSavesNothingWhenOutOfStock() throws Exception {
        when(inventory.getStock("pen")).thenReturn(new InventoryClient.StockResponse("pen", 0));

        mvc.perform(postOrder("{\"sku\":\"pen\",\"qty\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("422"))
                .andExpect(jsonPath("$.error").exists());
        verify(orders, never()).save(any());
    }

    @Test
    void returns404ForUnknownSku() throws Exception {
        when(inventory.getStock("foo")).thenThrow(new UnknownSkuException("Unknown sku"));

        mvc.perform(postOrder("{\"sku\":\"foo\",\"qty\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("404"));
        verify(orders, never()).save(any());
    }

    @Test
    void returns400WhenSkuMissing() throws Exception {
        mvc.perform(postOrder("{\"qty\":1}")).andExpect(status().isBadRequest());
        verify(inventory, never()).getStock(any());
    }

    @Test
    void returns400WhenQtyMissing() throws Exception {
        mvc.perform(postOrder("{\"sku\":\"book\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        mvc.perform(postOrder("{not json")).andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForWrongQtyType() throws Exception {
        mvc.perform(postOrder("{\"sku\":\"book\",\"qty\":\"abc\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void returns422WhenQtyBelowOne() throws Exception {
        mvc.perform(postOrder("{\"sku\":\"book\",\"qty\":0}"))
                .andExpect(status().isUnprocessableEntity());
        verify(inventory, never()).getStock(any());
    }

    @Test
    void returns422WhenSkuBlank() throws Exception {
        mvc.perform(postOrder("{\"sku\":\"  \",\"qty\":1}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returns503WhenInventoryUnavailable() throws Exception {
        when(inventory.getStock("book")).thenThrow(new InventoryUnavailableException("500"));

        mvc.perform(postOrder("{\"sku\":\"book\",\"qty\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("503"));
    }

    @Test
    void returns503WhenConnectionRefused() throws Exception {
        when(inventory.getStock("book")).thenThrow(retryable(new ConnectException("refused")));

        mvc.perform(postOrder("{\"sku\":\"book\",\"qty\":1}")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void returns504WhenInventoryTimesOut() throws Exception {
        when(inventory.getStock("book")).thenThrow(retryable(new SocketTimeoutException("Read timed out")));

        mvc.perform(postOrder("{\"sku\":\"book\",\"qty\":1}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value("504"));
    }

    @Test
    void returns429WhenInventoryRateLimits() throws Exception {
        when(inventory.getStock("book")).thenThrow(new InventoryRateLimitedException("slow down"));

        mvc.perform(postOrder("{\"sku\":\"book\",\"qty\":1}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("429"));
    }

    @Test
    void getsExistingOrder() throws Exception {
        when(orders.findById(1L)).thenReturn(Optional.of(new Order("book", 2, "CREATED")));

        mvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("book"));
    }

    @Test
    void returns404ForUnknownOrderId() throws Exception {
        when(orders.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/orders/99")).andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postOrder(String json) {
        return post("/orders").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static RetryableException retryable(Throwable cause) {
        Request request = Request.create(
                Request.HttpMethod.GET, "/inventory/book", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.GET, cause, (Long) null, request);
    }
}
