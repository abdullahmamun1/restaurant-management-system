package com.restaurant.service;

import com.restaurant.config.ReportingProperties;
import com.restaurant.controller.dto.SalesSummaryDto;
import com.restaurant.controller.dto.TopItemDto;
import com.restaurant.domain.DateRange;
import com.restaurant.repository.PaymentRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager reporting business logic (FR-23, FR-24).
 *
 * <p><strong>This service only reads.</strong> Nothing in M7 writes a row, mutates an entity or
 * takes a lock, and {@code readOnly = true} is not decoration: it tells the driver so, and it means
 * a {@code save()} added here later fails loudly instead of quietly committing.
 *
 * <p>Two things are deliberately <em>absent</em>, and their absence is the design:
 *
 * <ul>
 *   <li><strong>No {@code BillingProperties}, {@code BillCalculator} or {@code BillCharge}</strong>
 *       (M7 D1). Revenue is read from the frozen {@code payment} snapshot; this class is not given
 *       the means to recompute an amount from a rate, because a report that did so would let a
 *       config edit silently rewrite last month's takings.</li>
 *   <li><strong>No {@code SecurityContext}.</strong> Who may call is {@code @PreAuthorize}'s job and
 *       stays in the controller (NFR-03), as in every other service here.</li>
 * </ul>
 *
 * <p>Both methods begin by constructing one {@link DateRange}. That is the whole of M7 D5: the range
 * is interpreted in exactly one place, so the two panels of a dashboard cannot disagree about what
 * "27 July" means — the same reasoning that made {@code OrderIngredientDraw} shared.
 *
 * <p>FR-20's low-stock list is deliberately <em>not</em> here. It is already served by
 * {@link InventoryService#listLowStock()} behind {@code GET /inventory/ingredients/low-stock}, with
 * the same Manager-only audience and the same DTO; a second endpoint returning an identical answer
 * would be a second thing to keep in sync for no gain (M7 D6). The dashboard calls that one.
 */
@Service
public class ReportingService {

    /** FR-24 asks for "the top-selling items", not all of them; ten is a screenful. */
    static final int DEFAULT_TOP_ITEMS = 10;
    /**
     * An upper bound rather than a validation error: the cap exists to stop {@code limit=100000}
     * scanning the world, not to police the caller, and clamping is the conventional behaviour for
     * a page size. A malformed <em>date range</em> is the opposite case and is refused outright
     * (M7 D7/D8) — because there the caller's intent is genuinely unknowable, whereas
     * "as many as you can" plainly is not.
     */
    static final int MAX_TOP_ITEMS = 50;

    private final PaymentRepository paymentRepository;
    private final ReportingMapper mapper;
    private final ReportingProperties properties;

    public ReportingService(PaymentRepository paymentRepository,
                            ReportingMapper mapper,
                            ReportingProperties properties) {
        this.paymentRepository = paymentRepository;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * Total revenue, orders completed and the per-category breakdown for a date range (FR-23).
     *
     * <p>Two queries rather than one: the totals come from the {@code payment} snapshot and the
     * breakdown from the order lines beneath it. They are independent computations of overlapping
     * facts, which is exactly why the API suite asserts that the breakdown sums to
     * {@code itemRevenue} — two queries agreeing is a real check on both.
     *
     * <p>A range with no payments returns a well-formed zero, not a 404 (M7 D9): "how much did we
     * take last Monday?" has the answer "nothing".
     */
    @Transactional(readOnly = true)
    public SalesSummaryDto salesSummary(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return mapper.toSummary(
                range,
                paymentRepository.findSalesTotals(range.start(), range.endExclusive()),
                paymentRepository.findCategoryBreakdown(range.start(), range.endExclusive()));
    }

    /** The best-selling menu items in a date range, by quantity sold (FR-24). */
    @Transactional(readOnly = true)
    public List<TopItemDto> topItems(LocalDate from, LocalDate to, Integer limit) {
        DateRange range = range(from, to);
        return mapper.toTopItems(paymentRepository.findTopItems(
                range.start(), range.endExclusive(), PageRequest.of(0, clamp(limit))));
    }

    // ---- helpers -----------------------------------------------------------

    /** The single interpretation of a requested range, in the restaurant's zone (M7 D2, D5). */
    private DateRange range(LocalDate from, LocalDate to) {
        return new DateRange(from, to, properties.zone());
    }

    private int clamp(Integer limit) {
        if (limit == null) {
            return DEFAULT_TOP_ITEMS;
        }
        return Math.min(Math.max(limit, 1), MAX_TOP_ITEMS);
    }
}
