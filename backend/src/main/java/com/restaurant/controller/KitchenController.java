package com.restaurant.controller;

import com.restaurant.controller.dto.KitchenTicketDto;
import com.restaurant.service.KitchenService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kitchen Queue endpoints (FR-11). Thin controller — delegates to {@link KitchenService}.
 *
 * <p>RBAC per SRS §2.1: <em>Kitchen Queue</em> is a module of its own, and the only role with any
 * access to it is Kitchen (Manager, Waiter and Cashier are all "–"). That is a stricter rule than
 * the Table &amp; Orders module, where Kitchen itself only has Read — so the guard sits at class
 * level here rather than being repeated per method, making the boundary structural instead of
 * something a future endpoint can forget (NFR-03).
 *
 * <p>The kitchen's writes are lifecycle transitions on an order and live on
 * {@link OrderController#prepare} / {@link OrderController#ready}, guarded for the same role; the
 * split is by resource, not by role.
 */
@RestController
@RequestMapping("/kitchen")
@PreAuthorize("hasRole('KITCHEN')")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    /**
     * The live queue: CONFIRMED and PREPARING orders, oldest confirmation first (FR-11). Polled by
     * the kitchen display every ~2.5 s to satisfy NFR-02's 3-second freshness budget.
     */
    @GetMapping("/queue")
    public List<KitchenTicketDto> queue() {
        return kitchenService.getQueue();
    }
}
