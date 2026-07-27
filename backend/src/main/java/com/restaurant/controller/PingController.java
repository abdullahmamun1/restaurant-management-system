package com.restaurant.controller;

import com.restaurant.controller.dto.PingResponse;
import com.restaurant.service.PingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial liveness endpoint used to confirm the presentation &rarr; service wiring
 * during M0 scaffolding. Reachable at {@code GET /api/ping} (note the {@code /api}
 * context path). Replace/remove once real controllers land in M1+.
 */
@RestController
public class PingController {

    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse(pingService.status());
    }
}
