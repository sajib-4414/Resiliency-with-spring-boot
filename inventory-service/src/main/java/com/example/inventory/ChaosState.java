package com.example.inventory;

import org.springframework.stereotype.Component;

/** In-memory chaos switches applied to /inventory/** by {@link ChaosFilter}. */
@Component
public class ChaosState {

    private volatile long latencyMs = 0;
    private volatile double failureRate = 0.0;
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

    public void update(long latencyMs, double failureRate, boolean down) {
        this.latencyMs = latencyMs;
        this.failureRate = failureRate;
        this.down = down;
    }
}
