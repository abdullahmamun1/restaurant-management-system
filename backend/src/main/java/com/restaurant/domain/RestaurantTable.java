package com.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A dining table on the floor (SRS §2.4). Occupancy is a consequence of the order lifecycle, not
 * something a caller sets directly: {@link #occupy()} is driven by order creation (FR-06) and
 * {@link #release()} by payment (FR-15, M6). Maps to {@code restaurant_table}.
 */
@Entity
@Table(name = "restaurant_table")
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String label;

    @Column(nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TableStatus status = TableStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected RestaurantTable() {
        // Required by JPA.
    }

    public RestaurantTable(String label, int seats) {
        this.label = label;
        this.seats = seats;
        this.status = TableStatus.AVAILABLE;
    }

    /**
     * Seats a party by taking the table for a new order (FR-06).
     *
     * @throws TableUnavailableException if the table already has an open order.
     */
    public void occupy() {
        if (status != TableStatus.AVAILABLE) {
            throw new TableUnavailableException("Table " + label + " is " + status
                    + " and already has an open order. Settle it before starting a new one.");
        }
        this.status = TableStatus.OCCUPIED;
    }

    /** Frees the table once its order has been paid (FR-15) — called by M6's billing flow. */
    public void release() {
        this.status = TableStatus.AVAILABLE;
    }

    /**
     * Raises or clears the service flag on an occupied table. Purely informational — it does not
     * affect the order lifecycle, and a free table has nothing to flag.
     */
    public void setServiceFlag(boolean needsService) {
        if (status == TableStatus.AVAILABLE) {
            throw new TableUnavailableException(
                    "Table " + label + " is free — there is nothing to flag for service.");
        }
        this.status = needsService ? TableStatus.NEEDS_SERVICE : TableStatus.OCCUPIED;
    }

    public boolean isAvailable() {
        return status == TableStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public int getSeats() {
        return seats;
    }

    public TableStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
