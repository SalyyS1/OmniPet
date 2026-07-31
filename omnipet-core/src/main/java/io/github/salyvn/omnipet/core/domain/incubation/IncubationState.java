package io.github.salyvn.omnipet.core.domain.incubation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

public record IncubationState(
        UUID id,
        String eggId,
        IncubationOutcome outcome,
        long remainingActiveMillis,
        IncubationStatus status,
        List<UUID> appliedActionTokens,
        Map<String, Object> extensions) {
    public static final int MAX_ACTION_TOKENS = 128;

    public IncubationState {
        if (id == null) throw new IllegalArgumentException("incubation id is required");
        eggId = StableId.requireValid(eggId);
        if (outcome == null) throw new IllegalArgumentException("incubation outcome is required");
        if (remainingActiveMillis < 0 || remainingActiveMillis > outcome.totalActiveMillis()) {
            throw new IllegalArgumentException("remaining active time is outside the resolved duration");
        }
        if (status == null) throw new IllegalArgumentException("incubation status is required");
        if (status == IncubationStatus.INCUBATING && remainingActiveMillis == 0) {
            throw new IllegalArgumentException("incubating state must have remaining active time");
        }
        if ((status == IncubationStatus.READY || status == IncubationStatus.CLAIMED)
                && remainingActiveMillis != 0) {
            throw new IllegalArgumentException(status + " state must have zero remaining active time");
        }
        appliedActionTokens = List.copyOf(appliedActionTokens == null ? List.of() : appliedActionTokens);
        if (appliedActionTokens.size() > MAX_ACTION_TOKENS) {
            throw new IllegalArgumentException("too many applied incubation action tokens");
        }
        if (new HashSet<>(appliedActionTokens).size() != appliedActionTokens.size()) {
            throw new IllegalArgumentException("duplicate incubation action token");
        }
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "incubation.extensions");
    }

    public boolean terminal() {
        return status == IncubationStatus.CLAIMED
                || status == IncubationStatus.CANCELLED
                || status == IncubationStatus.FAILED;
    }

    public boolean hasApplied(UUID token) {
        return token != null && appliedActionTokens.contains(token);
    }
}
