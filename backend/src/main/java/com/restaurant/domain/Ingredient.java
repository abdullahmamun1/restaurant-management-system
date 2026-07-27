package com.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A raw material tracked in inventory (FR-17). Holds current stock, unit of measure, and a
 * low-stock threshold (FR-18). The stock invariant (never below zero, FR-22) is enforced here in
 * {@link #applyAdjustment(BigDecimal)} so it holds for every caller. Maps to {@code ingredient}.
 */
@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "stock_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockQty = BigDecimal.ZERO;

    @Column(name = "low_stock_threshold", nullable = false, precision = 12, scale = 3)
    private BigDecimal lowStockThreshold = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected Ingredient() {
        // Required by JPA.
    }

    public Ingredient(String name, String unit, BigDecimal initialStock, BigDecimal lowStockThreshold) {
        this.name = name;
        this.unit = unit;
        this.stockQty = initialStock;
        this.lowStockThreshold = lowStockThreshold;
    }

    /** Update descriptive fields only — stock is never edited directly (audit integrity). */
    public void updateDetails(String name, String unit, BigDecimal lowStockThreshold) {
        this.name = name;
        this.unit = unit;
        this.lowStockThreshold = lowStockThreshold;
    }

    /**
     * Applies a signed change to stock and returns the resulting quantity.
     *
     * @throws InsufficientStockException if the result would be negative (FR-22).
     */
    public BigDecimal applyAdjustment(BigDecimal delta) {
        BigDecimal result = stockQty.add(delta);
        if (result.signum() < 0) {
            throw new InsufficientStockException(
                    "Adjustment of " + delta + " would drop '" + name + "' below zero (current "
                            + stockQty + " " + unit + ").");
        }
        this.stockQty = result;
        return result;
    }

    /** True when at or below the configured low-stock threshold (FR-18). */
    public boolean isLowStock() {
        return stockQty.compareTo(lowStockThreshold) <= 0;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getStockQty() {
        return stockQty;
    }

    public BigDecimal getLowStockThreshold() {
        return lowStockThreshold;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
