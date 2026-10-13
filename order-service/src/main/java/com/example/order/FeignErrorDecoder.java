package com.example.order;

import com.example.order.InventoryExceptions.InventoryRateLimitedException;
import com.example.order.InventoryExceptions.InventoryUnavailableException;
import com.example.order.InventoryExceptions.UnknownSkuException;
import feign.Response;
import feign.codec.ErrorDecoder;

/** Registered for {@link InventoryClient} only, via {@link InventoryClientConfig}. */
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder fallback = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == 404) {
            return new UnknownSkuException("Unknown sku (" + methodKey + ")");
        }
        if (status == 429) {
            return new InventoryRateLimitedException("Inventory rate limit hit (" + methodKey + ")");
        }
        if (status >= 500) {
            return new InventoryUnavailableException("Inventory returned " + status + " (" + methodKey + ")");
        }
        return fallback.decode(methodKey, response);
    }
}
