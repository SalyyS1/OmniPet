package io.github.salyvn.omnipet.core.incubation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;

final class IncubationStateChanges {
    private IncubationStateChanges() {}

    static IncubationState remaining(IncubationState current, long remaining, UUID actionToken) {
        IncubationStatus status = remaining == 0 ? IncubationStatus.READY : IncubationStatus.INCUBATING;
        List<UUID> tokens = actionToken == null ? current.appliedActionTokens() : appendToken(current, actionToken);
        return copy(current, remaining, status, tokens);
    }

    static IncubationState cancelled(IncubationState current, UUID actionToken) {
        return copy(current, current.remainingActiveMillis(), IncubationStatus.CANCELLED,
                appendToken(current, actionToken));
    }

    static IncubationState claimed(IncubationState current) {
        return copy(current, 0, IncubationStatus.CLAIMED, current.appliedActionTokens());
    }

    static long saturatingSubtract(long current, long reduction) {
        return reduction >= current ? 0 : current - reduction;
    }

    private static List<UUID> appendToken(IncubationState current, UUID token) {
        ArrayList<UUID> tokens = new ArrayList<>(current.appliedActionTokens());
        if (tokens.size() == IncubationState.MAX_ACTION_TOKENS) {
            throw new IllegalStateException("incubation action token capacity is exhausted");
        }
        tokens.add(token);
        return List.copyOf(tokens);
    }

    private static IncubationState copy(
            IncubationState current,
            long remaining,
            IncubationStatus status,
            List<UUID> tokens) {
        return new IncubationState(
                current.id(), current.eggId(), current.outcome(), remaining, status, tokens, current.extensions());
    }
}
