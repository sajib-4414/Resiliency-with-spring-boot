# Lessons

## Feign ErrorDecoder: global vs per-client

**What it is.** An `ErrorDecoder` is the piece Feign (via Spring Cloud OpenFeign) calls whenever a
response is not 2xx. It translates the HTTP status into an exception, e.g. 404 ->
`UnknownSkuException`, 5xx -> `InventoryUnavailableException`. We never call it ourselves.
Timeouts and connection refused are not HTTP responses, so Feign wraps them in a
`RetryableException` instead and the decoder is skipped.

**Flow.** `OrderController` -> Feign proxy (`InventoryClient`) -> HTTP -> non-2xx ->
`ErrorDecoder` -> exception -> `ApiExceptionHandler` -> JSON error response.

**Two ways to register it**

| | Global | Per client (what we use) |
|---|---|---|
| How | `@Component` on the decoder class | `@Bean` method in a config class, referenced by `@FeignClient(configuration = ...)` |
| Scope | Every Feign client in the app | Only the client that names the config class |
| Use when | All clients share the same error rules | Each service needs its own mapping |

**Per-client setup**
```java
// no @Configuration / @Component here!
public class InventoryClientConfig {
    @Bean ErrorDecoder inventoryErrorDecoder() { return new FeignErrorDecoder(); }
}

@FeignClient(name = "inventory", url = "${inventory.url}", configuration = InventoryClientConfig.class)
public interface InventoryClient { ... }
```

**The trap.** If the config class has `@Configuration` (or `@Component`) and sits under the scanned
package, component scanning registers its beans globally and the "per client" scope is lost.

**`@Bean` vs `@Component`.** `@Component` on a class: Spring calls `new` for you. `@Bean` on a method:
you build the object and Spring stores the return value, so you control construction and scope.

**Other per-client beans** that fit in the same config class: `Retryer` (keep it `NEVER_RETRY`, retries
belong to Resilience4j), `RequestInterceptor` (headers), `Request.Options` (timeouts), `Logger.Level`.
Timeouts stay in `application.yml` here because they are easy to change per experiment.
