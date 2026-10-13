package com.example.inventory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lab-only fault injector. Runs before the controller on every {@code /inventory/**} request and,
 * depending on the switches in {@link ChaosState}, makes the request slow or fail so that
 * order-service has something to survive. With all switches off it does nothing.
 */
@Component
public class ChaosFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChaosFilter.class);

    private final ChaosState state;

    public ChaosFilter(ChaosState state) {
        this.state = state;
    }

    /** Skip everything except /inventory/**, so /chaos and /h2-console still work even when down=true. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/inventory");
    }

    /**
     * Applies the switches in order: down, then latency, then random failure. Returning without
     * calling {@code chain.doFilter} ends the request here, so the controller never runs.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 1. down: every request fails immediately with 503.
        if (state.isDown()) {
            log.warn("CHAOS: down -> 503");
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "chaos: service down");
            return;
        }
        // 2. latency: hold the request for latencyMs, then carry on. Applies before the failure roll,
        //    so a randomly failed request is slow too (like a dependency that times out, then errors).
        if (state.getLatencyMs() > 0) {
            log.warn("CHAOS: sleeping {} ms", state.getLatencyMs());
            try {
                Thread.sleep(state.getLatencyMs());
            } catch (InterruptedException e) {
                // Don't swallow the interrupt: restore the flag so the server can shut the thread down.
                Thread.currentThread().interrupt();
            }
        }
        // 3. random failure: roll a number in [0, 1) and fail with 500 if it is below failureRate.
        //    0.0 never fails, 0.5 fails about half the requests (each request rolls independently,
        //    so it is not strictly every other one), 1.0 always fails.
        if (ThreadLocalRandom.current().nextDouble() < state.getFailureRate()) {
            log.warn("CHAOS: random failure -> 500");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "chaos: random failure");
            return;
        }
        // 4. no chaos triggered: hand the request on to the real controller.
        chain.doFilter(request, response);
    }
}
