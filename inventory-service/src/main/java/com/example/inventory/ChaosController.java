package com.example.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChaosController {

    private final ChaosState state;

    public ChaosController(ChaosState state) {
        this.state = state;
    }

    /** Example: POST /chaos {"latencyMs":3000,"failureRate":0.5,"down":false}. Send {} to reset. */
    @PostMapping("/chaos")
    public ChaosRequest set(@RequestBody ChaosRequest request) {
        state.update(request.latencyMs(), request.failureRate(), request.down());
        return request;
    }

    @GetMapping("/chaos")
    public ChaosRequest get() {
        return new ChaosRequest(state.getLatencyMs(), state.getFailureRate(), state.isDown());
    }

    public record ChaosRequest(long latencyMs, double failureRate, boolean down) {
    }
}
