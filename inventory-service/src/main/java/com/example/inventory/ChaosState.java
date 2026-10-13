package com.example.inventory;

import org.springframework.stereotype.Component;

/**
 * In-memory chaos switches applied to /inventory/** by {@link ChaosFilter}. A single shared instance:
 * {@link ChaosController} writes it, {@link ChaosFilter} reads it. Everything defaults to "off" and is
 * lost on restart.
 *
 * <p>The fields are {@code volatile} because one request thread writes them (POST /chaos) while many
 * other request threads read them; volatile makes a new value visible to all threads immediately.
 */
@Component
public class ChaosState {

    /** Extra delay added to every request, in milliseconds. 0 = no delay. */
    private volatile long latencyMs = 0;

    /** Chance that a request fails with 500, from 0.0 (never) to 1.0 (always). */
    private volatile double failureRate = 0.0;

    /** When true, every request fails with 503 straight away; the other two switches are ignored. */
    private volatile boolean down = false;

    public long getLatencyMs() {
        return latencyMs;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public boolean isDown() {
        return down;
    }

    /** Replaces all three switches at once. Passing 0, 0.0, false resets to normal behavior. */
    public void update(long latencyMs, double failureRate, boolean down) {
        this.latencyMs = latencyMs;
        this.failureRate = failureRate;
        this.down = down;
    }
}
