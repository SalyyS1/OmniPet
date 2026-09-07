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
        List<UUID> tokens = actionToken == null
                ? current.appliedActionTokens()
                // Reaching zero ends the incubation, so it is terminal in the same way cancelling is.
                : appendToken(current, actionToken, status == IncubationStatus.READY);
        return copy(current, remaining, status, tokens);
    }

    static IncubationState cancelled(IncubationState current, UUID actionToken) {
        return copy(current, current.remainingActiveMillis(), IncubationStatus.CANCELLED,
                appendToken(current, actionToken, true));
    }

    static IncubationState claimed(IncubationState current) {
        return copy(current, 0, IncubationStatus.CLAIMED, current.appliedActionTokens());
    }

    static long saturatingSubtract(long current, long reduction) {
        return reduction >= current ? 0 : current - reduction;
    }

    /**
     * Records an action token, unless this is the action that ends the incubation and there is no room.
     *
     * <p>The token list exists so a retried admin action is not applied twice. A terminal action does not
     * need it: once the incubation is READY or CANCELLED, a second attempt is refused by the status check
     * instead. That distinction is what keeps a full list from becoming a trap — an incubation at 128
     * tokens used to refuse cancel and complete along with everything else, so the only two actions that
     * could have ended it were the two it would not accept, and the egg was stuck for good.
     */
    private static List<UUID> appendToken(IncubationState current, UUID token, boolean terminal) {
        ArrayList<UUID> tokens = new ArrayList<>(current.appliedActionTokens());
        if (tokens.size() >= IncubationState.MAX_ACTION_TOKENS) {
            if (terminal) return current.appliedActionTokens();
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
