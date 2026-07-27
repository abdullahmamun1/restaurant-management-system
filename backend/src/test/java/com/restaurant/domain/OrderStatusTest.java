package com.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the {@code State} transition table (SRS §2.4). Pure domain — no Spring context
 * and no database, so these run on every {@code mvn test}.
 */
class OrderStatusTest {

    @Test
    @DisplayName("the lifecycle advances one step at a time, PENDING through PAID")
    void happyPathTransitions() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARING)).isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY)).isTrue();
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.SERVED)).isTrue();
        assertThat(OrderStatus.SERVED.canTransitionTo(OrderStatus.PAID)).isTrue();
    }

    @Test
    @DisplayName("steps cannot be skipped — the kitchen stages are not bypassable")
    void skippingStagesIsRejected() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PREPARING)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SERVED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isFalse();
        // FR-10 is reachable only once the kitchen has finished (READY), never straight from
        // CONFIRMED — this is the guard M4's /serve endpoint relies on.
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SERVED)).isFalse();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.SERVED)).isFalse();
    }

    @Test
    @DisplayName("the lifecycle never runs backwards")
    void backwardTransitionsAreRejected() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PENDING)).isFalse();
        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.PREPARING)).isFalse();
        assertThat(OrderStatus.SERVED.canTransitionTo(OrderStatus.READY)).isFalse();
    }

    @Test
    @DisplayName("PAID is terminal — no refunds, cancellations or edits after payment")
    void paidIsTerminal() {
        assertThat(OrderStatus.PAID.isTerminal()).isTrue();
        assertThat(OrderStatus.PAID.allowedTransitions()).isEmpty();
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.PAID.canTransitionTo(target)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("only PENDING is terminal-free and edit-capable; everything else locks items")
    void onlyPendingAllowsItemEdits(OrderStatus status) {
        assertThat(status.allowsItemEdits()).isEqualTo(status == OrderStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("a null target is never a legal transition")
    void nullTargetIsRejected(OrderStatus status) {
        assertThat(status.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("every state has a transition set, and only PAID's is empty")
    void transitionTableCoversEveryState() {
        Set<OrderStatus> withoutSuccessors = EnumSet.noneOf(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            if (status.allowedTransitions().isEmpty()) {
                withoutSuccessors.add(status);
            }
        }
        assertThat(withoutSuccessors).containsExactly(OrderStatus.PAID);
    }
}
