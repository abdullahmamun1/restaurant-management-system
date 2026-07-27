package com.restaurant.controller;

import com.restaurant.controller.dto.SalesSummaryDto;
import com.restaurant.controller.dto.TopItemDto;
import com.restaurant.service.ReportingService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reporting endpoints (FR-23, FR-24). Thin controller — delegates to {@link ReportingService}.
 *
 * <p>RBAC per SRS §2.1, where Reports &amp; Alerts is <strong>Manager: Full</strong> and no other
 * role has any access at all. The guard therefore sits at <em>class</em> level, as on
 * {@code InventoryController} and {@code KitchenController}: where a whole module belongs to one
 * role, putting the rule on the class means a later endpoint cannot forget it.
 *
 * <p><strong>FR-20 is not here.</strong> The manager dashboard's low-stock panel is served by the
 * existing {@code GET /inventory/ingredients/low-stock} — same query, same DTO, same Manager-only
 * audience — so a {@code /reports/low-stock} alias would be a second URL to keep in sync for no gain
 * (M7 D6). Contrast {@code KitchenTicketDto}, where a second view of the same data <em>was</em>
 * right, because there the audience genuinely differed.
 *
 * <p>{@code from} and {@code to} are required. There is no server-side default of "today": the
 * dashboard picks its own default range and sends it, so exactly one place decides what an
 * unspecified range means.
 */
@RestController
@RequestMapping("/reports")
@PreAuthorize("hasRole('MANAGER')")
public class ReportsController {

    private final ReportingService reportingService;

    public ReportsController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /** Total revenue, orders completed and the breakdown by menu category for a range (FR-23). */
    @GetMapping("/sales")
    public SalesSummaryDto sales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.salesSummary(from, to);
    }

    /**
     * The top-selling menu items in a range, by quantity sold (FR-24). {@code limit} is optional and
     * clamped rather than validated — see {@code ReportingService.MAX_TOP_ITEMS}.
     */
    @GetMapping("/top-items")
    public List<TopItemDto> topItems(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return reportingService.topItems(from, to, limit);
    }
}
