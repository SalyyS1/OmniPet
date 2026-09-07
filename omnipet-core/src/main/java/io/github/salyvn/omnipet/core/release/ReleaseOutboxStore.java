package io.github.salyvn.omnipet.core.release;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

final class ReleaseOutboxStore {
    static final String EXTENSION_KEY = "omnipetReleaseOutboxV1";
    static final int MAX_ENTRIES = 256;
    private static final Set<String> ENTRY_KEYS = Set.of(
            "transactionId", "playerId", "petId", "petFingerprint", "previewToken", "rewards",
            "internalState", "externalState", "externalEvidence");

    Optional<ReleaseOutboxEntry> find(PlayerState state, UUID transactionId) {
        Object root = state.extensions().get(EXTENSION_KEY);
        if (!(root instanceof Map<?, ?> map)) return Optional.empty();
        Object raw = map.get(transactionId.toString());
        if (!(raw instanceof Map<?, ?> entry)) return Optional.empty();
        return Optional.of(decode(entry));
    }

    int size(PlayerState state) {
        Object root = state.extensions().get(EXTENSION_KEY);
        return root instanceof Map<?, ?> map ? map.size() : 0;
    }

    List<ReleaseOutboxEntry> list(PlayerState state, int limit) {
        if (limit < 1 || limit > MAX_ENTRIES) {
            throw new IllegalArgumentException("release outbox list limit must be 1.." + MAX_ENTRIES);
        }
        Object root = state.extensions().get(EXTENSION_KEY);
        if (root == null) return List.of();
        if (!(root instanceof Map<?, ?> map)) throw new IllegalArgumentException("release outbox is invalid");
        ArrayList<String> keys = new ArrayList<>();
        map.keySet().forEach(key -> keys.add(String.valueOf(key)));
        keys.sort(String::compareTo);
        ArrayList<ReleaseOutboxEntry> entries = new ArrayList<>();
        for (String key : keys) {
            Object raw = map.get(key);
            if (!(raw instanceof Map<?, ?> encoded)) throw new IllegalArgumentException("release outbox entry is invalid");
            ReleaseOutboxEntry entry = decode(encoded);
            if (!entry.transactionId().toString().equals(key)) {
                throw new IllegalArgumentException("release outbox key identity mismatch");
            }
            entries.add(entry);
            if (entries.size() == limit) break;
        }
        return List.copyOf(entries);
    }

    /**
     * Whether the whole outbox can be read, not merely whether its container is the right shape.
     *
     * <p>This used to check only that the root was a map, so a single malformed entry inside passed the
     * check and then threw out of {@code list} or {@code find} — the caller asked "is this usable?",
     * was told yes, and got an exception anyway. Callers use this to answer with an OUTBOX_INVALID
     * status instead of failing, and that only works if the answer covers the entries too.
     */
    boolean isUsable(PlayerState state) {
        Object root = state.extensions().get(EXTENSION_KEY);
        if (root == null) return true;
        if (!(root instanceof Map<?, ?> map)) return false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> encoded)) return false;
            try {
                ReleaseOutboxEntry decoded = decode(encoded);
                if (!decoded.transactionId().toString().equals(String.valueOf(entry.getKey()))) return false;
            } catch (RuntimeException malformed) {
                return false;
            }
        }
        return true;
    }

    PlayerState release(PlayerState state, UUID petId, ReleaseOutboxEntry entry) {
        List<io.github.salyvn.omnipet.core.domain.PetInstance> pets = state.pets().stream()
                .filter(pet -> !pet.id().equals(petId))
                .toList();
        List<UUID> desired = state.desiredActivePetIds().stream()
                .filter(id -> !id.equals(petId))
                .toList();
        return copy(state, pets, desired, extensionsWith(state, entry));
    }

    PlayerState update(PlayerState state, ReleaseOutboxEntry entry) {
        return copy(state, state.pets(), state.desiredActivePetIds(), extensionsWith(state, entry));
    }

    private Map<String, Object> extensionsWith(PlayerState state, ReleaseOutboxEntry entry) {
        LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(RawNodeValues.mutableMap(state.extensions()));
        LinkedHashMap<String, Object> outbox = new LinkedHashMap<>();
        Object existing = extensions.get(EXTENSION_KEY);
        if (existing instanceof Map<?, ?> map) {
            map.forEach((key, value) -> outbox.put(String.valueOf(key), RawNodeValues.mutableCopy(value)));
        }
        outbox.put(entry.transactionId().toString(), encode(entry));
        extensions.put(EXTENSION_KEY, outbox);
        return extensions;
    }

    private static PlayerState copy(
            PlayerState state,
            List<io.github.salyvn.omnipet.core.domain.PetInstance> pets,
            List<UUID> desired,
            Map<String, Object> extensions) {
        return new PlayerState(
                state.playerId(), state.revision(), pets, state.vaultCapacity(), state.activeSlotCount(), desired,
                state.slotEntitlements(), state.legacyCurrentEgg(), state.incubation(), extensions);
    }

    private static Map<String, Object> encode(ReleaseOutboxEntry entry) {
        LinkedHashMap<String, Object> raw = new LinkedHashMap<>(RawNodeValues.mutableMap(entry.extensions()));
        raw.put("transactionId", entry.transactionId().toString());
        raw.put("playerId", entry.playerId().toString());
        raw.put("petId", entry.petId().toString());
        raw.put("petFingerprint", entry.petFingerprint());
        raw.put("previewToken", entry.previewToken());
        raw.put("rewards", ReleaseIdentity.rewardMap(entry.rewards()));
        raw.put("internalState", entry.internalState().name());
        raw.put("externalState", entry.externalState().name());
        if (!entry.externalEvidence().isBlank()) raw.put("externalEvidence", entry.externalEvidence());
        else raw.remove("externalEvidence");
        return raw;
    }

    private static ReleaseOutboxEntry decode(Map<?, ?> input) {
        Map<String, Object> raw = stringMap(input);
        UUID transactionId = uuid(raw, "transactionId");
        UUID playerId = uuid(raw, "playerId");
        UUID petId = uuid(raw, "petId");
        ReleaseRewardBundle rewards = decodeRewards(raw.get("rewards"));
        LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(raw);
        ENTRY_KEYS.forEach(extensions::remove);
        return new ReleaseOutboxEntry(
                transactionId,
                playerId,
                petId,
                text(raw, "petFingerprint"),
                text(raw, "previewToken"),
                rewards,
                ReleaseOutboxEntry.InternalState.valueOf(text(raw, "internalState")),
                ReleaseOutboxEntry.ExternalState.valueOf(text(raw, "externalState")),
                String.valueOf(raw.getOrDefault("externalEvidence", "")),
                extensions);
    }

    private static ReleaseRewardBundle decodeRewards(Object input) {
        if (!(input instanceof Map<?, ?> map)) throw new IllegalArgumentException("release rewards must be a map");
        Map<String, Object> rewards = stringMap(map);
        List<ReleaseRewardBundle.InternalReward> internal = new ArrayList<>();
        for (Map<String, Object> raw : mapList(rewards.get("internal"))) {
            internal.add(new ReleaseRewardBundle.InternalReward(
                    text(raw, "rewardId"), longValue(raw, "amount"), payload(raw.get("payload"))));
        }
        List<ReleaseRewardBundle.ExternalReward> external = new ArrayList<>();
        for (Map<String, Object> raw : mapList(rewards.get("external"))) {
            external.add(new ReleaseRewardBundle.ExternalReward(
                    text(raw, "provider"), text(raw, "rewardId"),
                    new BigDecimal(text(raw, "amount")), payload(raw.get("payload"))));
        }
        return new ReleaseRewardBundle(internal, external);
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("release reward list is invalid");
        return list.stream().map(item -> {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException("release reward entry is invalid");
            return stringMap(map);
        }).toList();
    }

    private static Map<String, Object> payload(Object value) {
        return value instanceof Map<?, ?> map ? stringMap(map) : Map.of();
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("missing release outbox field: " + key);
        }
        return String.valueOf(value);
    }

    private static UUID uuid(Map<String, Object> map, String key) {
        return UUID.fromString(text(map, key));
    }

    /**
     * Reads a whole-number field, refusing a value that would not survive the trip.
     *
     * <p>{@code Number.longValue()} silently truncates: a reward amount stored as a double, or an epoch
     * in a field too wide for it, came back as a different number than was written with nothing to say
     * so. A value that cannot be represented exactly is treated as a malformed entry, which the caller
     * reports as an invalid outbox rather than acting on a mangled amount.
     */
    private static long longValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            long exact = number.longValue();
            if (!Double.isFinite(decimal) || decimal != exact) {
                throw new IllegalArgumentException("release outbox " + key + " is not an exact whole number");
            }
            return exact;
        }
        return Long.parseLong(text(map, key));
    }
}
