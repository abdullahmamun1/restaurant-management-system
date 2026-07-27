package com.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An immutable audit record of one manual stock change (FR-21, NFR-06): who changed it, by how
 * much (signed), why, the resulting stock, and when. Written once and never updated or deleted —
 * the application exposes no mutation path. Maps to {@code inventory_adjustment}.
 */
@Entity
@Table(name = "inventory_adjustment")
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Column(name = "quantity_change", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityChange;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "resulting_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal resultingStock;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected InventoryAdjustment() {
        // Required by JPA.
    }

    public InventoryAdjustment(Ingredient ingredient, User manager, BigDecimal quantityChange,
                               String reason, BigDecimal resultingStock) {
        this.ingredient = ingredient;
        this.manager = manager;
        this.quantityChange = quantityChange;
        this.reason = reason;
        this.resultingStock = resultingStock;
    }

    public Long getId() {
        return id;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public User getManager() {
        return manager;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getResultingStock() {
        return resultingStock;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
