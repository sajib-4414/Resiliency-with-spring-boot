package com.example.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * The stub blocks every request on a latch so calls stay in flight; bulkhead shrunk to 2 permits.
 */
@SpringBootTest(properties = {
        "resilience4j.bulkhead.instances.inventory.maxConcurrentCalls=2"
})
class InventoryBulkheadTest {

    static final HttpServer inventory = startStub();
    static final AtomicInteger calls = new AtomicInteger();
    static volatile CountDownLatch release = new CountDownLatch(1);

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
        release.countDown();
        inventory.stop(0);
    }

    @BeforeEach
    void reset() {
        calls.set(0);
        release = new CountDownLatch(1);
        registry.circuitBreaker("inventory").reset();
    }

    @Test
    void rejectsCallsBeyondTheLimitWithoutRetryOrReachingInventory() throws Exception {
        List<CompletableFuture<?>> inFlight = List.of(
                CompletableFuture.supplyAsync(() -> client.getStock("book")),
                CompletableFuture.supplyAsync(() -> client.getStock("book")));
        waitUntil(() -> calls.get() == 2);

        assertThrows(BulkheadFullException.class, () -> client.getStock("book"));
        assertEquals(2, calls.get());   // rejected before reaching inventory, and not retried
        assertEquals(CircuitBreaker.State.CLOSED, registry.circuitBreaker("inventory").getState());

        release.countDown();
        for (CompletableFuture<?> f : inFlight) {
            f.get(5, TimeUnit.SECONDS);
        }
        assertEquals(10, client.getStock("book").available());   // permits are released
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition not reached in time");
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.createContext("/inventory", exchange -> {
                calls.incrementAndGet();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = "{\"sku\":\"book\",\"available\":10}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
