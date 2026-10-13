package com.example.order;

import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Logs every retry of the "inventory" call so the attempts are visible in the console. */
@Component
public class RetryLogger {

    private static final Logger log = LoggerFactory.getLogger(RetryLogger.class);

    private final RetryRegistry registry;

    public RetryLogger(RetryRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void register() {
        var events = registry.retry("inventory").getEventPublisher();
        // Fires before each retry: retry #1 is the 2nd attempt overall.
        events.onRetry(e -> log.warn("RETRY #{} for inventory in {} ms, last error: {}",
                e.getNumberOfRetryAttempts(), e.getWaitInterval().toMillis(), e.getLastThrowable().toString()));
        // Fires when every attempt failed and we give up.
        events.onError(e -> log.error("RETRY GAVE UP for inventory after {} attempts, last error: {}",
                e.getNumberOfRetryAttempts(), e.getLastThrowable().toString()));
        // Fires when a retry finally worked.
        events.onSuccess(e -> {
            if (e.getNumberOfRetryAttempts() > 0) {
                log.info("RETRY SUCCEEDED for inventory after {} retries", e.getNumberOfRetryAttempts());
            }
        });
    }
}
