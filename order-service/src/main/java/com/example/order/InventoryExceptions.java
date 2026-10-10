package com.example.order;

/** Domain exceptions produced by {@link FeignErrorDecoder} so resilience config can pick what to retry. */
public final class InventoryExceptions {

    private InventoryExceptions() {
    }

    /** 404 from inventory: a real answer, never retry. */
    public static class UnknownSkuException extends RuntimeException {
        public UnknownSkuException(String message) {
            super(message);
        }
    }

    /** 429 from inventory: we are being rate limited. */
    public static class InventoryRateLimitedException extends RuntimeException {
        public InventoryRateLimitedException(String message) {
            super(message);
        }
    }

    /** 5xx from inventory: transient, retryable. */
    public static class InventoryUnavailableException extends RuntimeException {
        public InventoryUnavailableException(String message) {
            super(message);
        }
    }

    /** Business rule: not enough stock. */
    public static class OutOfStockException extends RuntimeException {
        public OutOfStockException(String message) {
            super(message);
        }
    }
}
