package com.restaurant.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * An inclusive calendar-date range, resolved against the restaurant's timezone into the half-open
 * instant interval {@code [start, endExclusive)} that the report queries actually filter on
 * (FR-23, FR-24).
 *
 * <p>A value object, like {@link Bill} — no persistence, no Spring, unit-testable with a fixed zone.
 * Two decisions are load-bearing and are stated here so nobody "simplifies" them:
 *
 * <p><strong>1. Half-open on purpose.</strong> The obvious alternative — {@code paid_at <= to
 * 23:59:59} — silently drops a payment taken in the last second of the day, because
 * {@code TIMESTAMPTZ} carries microseconds. Asking for the day <em>after</em> {@code to} and
 * excluding it leaves no gap to fall into, at any precision.
 *
 * <p><strong>2. Zoned on purpose.</strong> "Sales for the 27th" is a <em>local-day</em> question,
 * and the server may well run in UTC. At {@code +06} a payment taken at 19:30 UTC belongs to the
 * next local day; resolving the range in the restaurant's zone is what puts it in the right report.
 * The zone is configuration ({@code app.reporting.zone}) and never {@code ZoneId.systemDefault()},
 * so a report means the same thing wherever the application is deployed.
 *
 * <p>Every report query is handed the bounds from <em>one</em> instance of this class, for the same
 * reason {@code OrderIngredientDraw} is shared between the FR-08 check and the FR-19 deduction: if
 * two queries interpreted the same {@code from}/{@code to} differently, two panels of one dashboard
 * would disagree with each other on screen. One object makes that impossible by construction.
 */
public record DateRange(LocalDate from, LocalDate to, ZoneId zone) {

    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(zone, "zone");
        if (to.isBefore(from)) {
            throw new InvalidDateRangeException(
                    "'to' (" + to + ") must not be before 'from' (" + from + ").");
        }
    }

    /** The first instant in the range: midnight at the start of {@code from}, locally. */
    public OffsetDateTime start() {
        return from.atStartOfDay(zone).toOffsetDateTime();
    }

    /**
     * The first instant <em>after</em> the range: midnight at the start of the day following
     * {@code to}. Exclusive — see the half-open note on the class.
     */
    public OffsetDateTime endExclusive() {
        return to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
    }
}
