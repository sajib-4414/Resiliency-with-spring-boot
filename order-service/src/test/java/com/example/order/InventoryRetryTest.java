package com.example.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.order.InventoryExceptions.InventoryUnavailableException;
import com.example.order.InventoryExceptions.UnknownSkuException;
import com.sun.net.httpserver.HttpServer;
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
 * Retry is a Spring AOP proxy around the Feign client, so it only exists in a real Spring context
 * (a plain Mockito test cannot see the @Retry annotation). A tiny in-JVM HTTP server stands in for
 * inventory-service: it counts the calls it receives and answers with a status we choose.
 */
@SpringBootTest(properties = "resilience4j.retry.instances.inventory.waitDuration=10ms")
class InventoryRetryTest {

    static final HttpServer inventory = startStub();
    static final AtomicInteger calls = new AtomicInteger();
    static volatile int failFirst;      // this many calls fail with 500, then succeed
    static volatile int errorStatus = 500;

    @Autowired
    InventoryClient client;

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
        failFirst = 0;
        errorStatus = 500;
    }

    @Test
    void retriesTransientFailuresUntilSuccess() {
        failFirst = 2;

        assertEquals(10, client.getStock("book").available());
        assertEquals(3, calls.get());
    }

    @Test
    void givesUpAfterFourAttempts() {
        failFirst = 100;

        assertThrows(InventoryUnavailableException.class, () -> client.getStock("book"));
        assertEquals(4, calls.get());
    }

    @Test
    void doesNotRetryUnknownSku() {
        failFirst = 100;
        errorStatus = 404;

        assertThrows(UnknownSkuException.class, () -> client.getStock("foo"));
        assertEquals(1, calls.get());
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/inventory", exchange -> {
                boolean fail = calls.incrementAndGet() <= failFirst;
                byte[] body = fail ? new byte[0] : "{\"sku\":\"book\",\"available\":10}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(fail ? errorStatus : 200, fail ? -1 : body.length);
                if (!fail) {
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
