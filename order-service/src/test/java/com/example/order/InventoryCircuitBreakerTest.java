package com.example.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.order.InventoryExceptions.InventoryUnavailableException;
import com.example.order.InventoryExceptions.UnknownSkuException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/*
 * Like InventoryRetryTest: needs the Spring AOP proxy, with a stub HTTP server as inventory.
 * Window shrunk to 4 so a single failing call (4 retry attempts) is enough to open the circuit.
 */
@SpringBootTest(properties = {
        "resilience4j.retry.instances.inventory.waitDuration=10ms",
        "resilience4j.circuitbreaker.instances.inventory.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.inventory.minimumNumberOfCalls=4"
})
class InventoryCircuitBreakerTest {

    static final HttpServer inventory = startStub();
    static final AtomicInteger calls = new AtomicInteger();
    static volatile int status = 200;

    @Autowired
    InventoryClient client;

    @Autowired
    CircuitBreakerRegistry registry;

    @DynamicPropertySource
    static void inventoryUrl(DynamicPropertyRegistry registry) {
        registry.add("inventory.url", () -> "http://localhost:" + inventory.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        inventory.stop(0);
    }

    @BeforeEach
    void reset() {
        calls.set(0);
        status = 200;
        registry.circuitBreaker("inventory").reset();
    }

    @Test
    void opensAfterFailuresAndThenFailsFastWithoutCallingInventory() {
        status = 500;
        assertThrows(InventoryUnavailableException.class, () -> client.getStock("book"));
        assertEquals(4, calls.get());
        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker("inventory").getState());

        assertThrows(CallNotPermittedException.class, () -> client.getStock("book"));
        assertEquals(4, calls.get());   // rejected before reaching inventory, and not retried
    }

    @Test
    void realAnswersDoNotOpenTheCircuit() {
        status = 404;
        for (int i = 0; i < 6; i++) {
            assertThrows(UnknownSkuException.class, () -> client.getStock("foo"));
        }
        assertEquals(CircuitBreaker.State.CLOSED, registry.circuitBreaker("inventory").getState());
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/inventory", exchange -> {
                calls.incrementAndGet();
                boolean ok = status == 200;
                byte[] body = ok ? "{\"sku\":\"book\",\"available\":10}".getBytes(StandardCharsets.UTF_8) : new byte[0];
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, ok ? body.length : -1);
                if (ok) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
