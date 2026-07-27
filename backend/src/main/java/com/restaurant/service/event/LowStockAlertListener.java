package com.restaurant.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to an ingredient falling to or below its threshold (FR-18).
 *
 * <p><strong>Design pattern: Observer</strong>, via Spring's {@code ApplicationEvent}. It earns its
 * place here for one specific reason: <em>alerting must not be able to affect the payment.</em>
 * Listening on {@link TransactionPhase#AFTER_COMMIT} gives two guarantees that a direct call could
 * not — a payment that rolls back raises no alert at all, and a listener that fails cannot fail the
 * payment that triggered it. The publisher (the billing transaction) genuinely does not know or
 * care who is listening, which is the condition under which Observer is the right answer rather
 * than decoration.
 *
 * <p>What it does <em>not</em> do is deliver the user-facing alert. FR-20's low-stock dashboard is
 * a query — {@code GET /inventory/low-stock}, built in M3 — and a manager reads it on demand, so
 * pushing a notification here would duplicate it. This listener logs a WARN, which is the useful
 * behaviour today, and stands as the seam for real notifications later.
 */
@Component
public class LowStockAlertListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockAlertListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockDetectedEvent event) {
        log.warn("FR-18 low stock: '{}' (id {}) is down to {} {} (threshold {} {}).",
                event.name(), event.ingredientId(), event.remaining().toPlainString(), event.unit(),
                event.threshold().toPlainString(), event.unit());
    }
}
