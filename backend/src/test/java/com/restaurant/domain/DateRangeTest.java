package com.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the M7 D2 date-range resolution. Pure — a value object with a fixed zone, no
 * Spring and no database.
 *
 * <p>Two properties are worth pinning hard, because both fail <em>silently</em> in production: a
 * payment in the last microsecond of a day must be inside that day's range (the bug the half-open
 * interval exists to prevent), and the range must be resolved in the restaurant's zone rather than
 * the server's (at +06 those disagree for six hours of every day).
 */
class DateRangeTest {

    /** The configured default — deliberately not UTC, so a zone that is ignored shows up. */
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private DateRange range(String from, String to) {
        return new DateRange(LocalDate.parse(from), LocalDate.parse(to), DHAKA);
    }

    @Test
    @DisplayName("a single-day range starts at local midnight and covers exactly 24 hours")
    void singleDayCoversTwentyFourHours() {
        DateRange range = range("2026-07-27", "2026-07-27");

        assertThat(Duration.between(range.start(), range.endExclusive())).isEqualTo(Duration.ofDays(1));
        assertThat(range.start().toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
    }

    @Test
    @DisplayName("the end bound is midnight of the day AFTER 'to', and is exclusive")
    void endIsTheStartOfTheFollowingDay() {
        DateRange range = range("2026-07-01", "2026-07-27");

        assertThat(range.endExclusive().toLocalDate()).isEqualTo(LocalDate.parse("2026-07-28"));
        assertThat(range.endExclusive().toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
    }

    @Test
    @DisplayName("a payment in the last microsecond of the last day is inside the range")
    void lastMicrosecondOfTheDayIsIncluded() {
        DateRange range = range("2026-07-27", "2026-07-27");

        // The exact instant that `paid_at <= to 23:59:59` would drop. TIMESTAMPTZ keeps microseconds.
        OffsetDateTime lastMoment = OffsetDateTime.parse("2026-07-27T23:59:59.999999+06:00");

        assertThat(lastMoment).isAfterOrEqualTo(range.start());
        assertThat(lastMoment).isBefore(range.endExclusive());
    }

    @Test
    @DisplayName("midnight exactly belongs to the new day, not the old one")
    void midnightBelongsToTheFollowingDay() {
        OffsetDateTime midnight = OffsetDateTime.parse("2026-07-28T00:00:00+06:00");

        // Exclusive end: not in the 27th...
        assertThat(midnight).isEqualTo(range("2026-07-27", "2026-07-27").endExclusive());
        // ...and inclusive start: in the 28th. No instant falls in both ranges, and none in neither.
        assertThat(midnight).isEqualTo(range("2026-07-28", "2026-07-28").start());
    }

    @Test
    @DisplayName("the range is resolved in the restaurant's zone, not the server's")
    void resolvesInTheConfiguredZone() {
        DateRange range = range("2026-07-27", "2026-07-27");

        // Asia/Dhaka is +06, so the local day begins at 18:00Z the previous day. If the zone were
        // being ignored (or systemDefault() used on a UTC server) this would be 2026-07-27T00:00Z.
        assertThat(range.start().withOffsetSameInstant(ZoneOffset.UTC))
                .isEqualTo(OffsetDateTime.parse("2026-07-26T18:00:00Z"));

        // The concrete case this protects: a payment at 19:30 UTC on the 26th is already the 27th
        // locally, and belongs to the 27th's sales.
        assertThat(OffsetDateTime.parse("2026-07-26T19:30:00Z"))
                .isAfterOrEqualTo(range.start())
                .isBefore(range.endExclusive());
    }

    @Test
    @DisplayName("a range covering several days spans all of them")
    void multiDayRangeSpansEveryDay() {
        DateRange range = range("2026-07-01", "2026-07-31");

        assertThat(Duration.between(range.start(), range.endExclusive()))
                .isEqualTo(Duration.ofDays(31));
    }

    @Test
    @DisplayName("'to' before 'from' is refused rather than answered with a confident zero")
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> range("2026-07-27", "2026-07-01"))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("2026-07-27")
                .hasMessageContaining("2026-07-01");
    }

    @Test
    @DisplayName("a single-day range (from == to) is valid")
    void sameDayIsValid() {
        assertThat(range("2026-07-27", "2026-07-27")).isNotNull();
    }

    @Test
    @DisplayName("null bounds are rejected outright")
    void nullBoundsAreRejected() {
        LocalDate day = LocalDate.parse("2026-07-27");

        assertThatThrownBy(() -> new DateRange(null, day, DHAKA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DateRange(day, null, DHAKA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DateRange(day, day, null))
                .isInstanceOf(NullPointerException.class);
    }
}
