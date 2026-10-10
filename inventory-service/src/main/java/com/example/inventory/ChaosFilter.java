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

@Component
public class ChaosFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChaosFilter.class);

    private final ChaosState state;

    public ChaosFilter(ChaosState state) {
        this.state = state;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/inventory");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (state.isDown()) {
            log.warn("CHAOS: down -> 503");
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "chaos: service down");
            return;
        }
        if (state.getLatencyMs() > 0) {
            log.warn("CHAOS: sleeping {} ms", state.getLatencyMs());
            try {
                Thread.sleep(state.getLatencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < state.getFailureRate()) {
            log.warn("CHAOS: random failure -> 500");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "chaos: random failure");
            return;
        }
        chain.doFilter(request, response);
    }
}
