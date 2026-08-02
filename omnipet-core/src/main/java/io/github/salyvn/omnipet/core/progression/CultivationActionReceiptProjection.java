package io.github.salyvn.omnipet.core.progression;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** Temporary proof that progression applied before its item-action journal committed. */
public final class CultivationActionReceiptProjection {
    public static final String COMPONENT_KEY = "cultivationPendingActions";
    public static final int MAX_PENDING = 32;

    private CultivationActionReceiptProjection() {}

    public static boolean matches(PetInstance pet, CultivationItemActionTransaction action) {
        Map<String, Object> pending = pending(pet);
        Object raw = pending.get(action.actionToken().toString());
        if (!(raw instanceof Map<?, ?> receipt)) return false;
        return action.petId().toString().equals(String.valueOf(receipt.get("petId")))
                && action.item().fingerprint().equals(String.valueOf(receipt.get("fingerprint")))
                && action.kind().name().equals(String.valueOf(receipt.get("kind")));
    }

    public static PetInstance add(PetInstance pet, CultivationItemActionTransaction action) {
        Map<String, Object> pending = pending(pet);
        String key = action.actionToken().toString();
        if (pending.containsKey(key)) {
            if (!matches(pet, action)) throw new IllegalArgumentException("cultivation receipt identity changed");
            return pet;
        }
        if (pending.size() >= MAX_PENDING) throw new IllegalStateException("cultivation receipt capacity reached");
        LinkedHashMap<String, Object> next = new LinkedHashMap<>(pending);
        next.put(key, Map.of(
                "petId", action.petId().toString(),
                "fingerprint", action.item().fingerprint(),
                "kind", action.kind().name()));
        return write(pet, next);
    }

    public static PetInstance remove(PetInstance pet, CultivationItemActionTransaction action) {
        Map<String, Object> pending = pending(pet);
        if (!pending.containsKey(action.actionToken().toString())) return pet;
        if (!matches(pet, action)) throw new IllegalArgumentException("cultivation receipt identity changed");
        LinkedHashMap<String, Object> next = new LinkedHashMap<>(pending);
        next.remove(action.actionToken().toString());
        return write(pet, next);
    }

    private static Map<String, Object> pending(PetInstance pet) {
        Object raw = pet.rawComponents().get(COMPONENT_KEY);
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> source)) throw new IllegalArgumentException("cultivation receipts must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(value)));
        if (result.size() > MAX_PENDING) throw new IllegalArgumentException("too many pending cultivation receipts");
        return result;
    }

    private static PetInstance write(PetInstance pet, Map<String, Object> pending) {
        LinkedHashMap<String, Object> components = new LinkedHashMap<>(pet.rawComponents());
        if (pending.isEmpty()) components.remove(COMPONENT_KEY);
        else components.put(COMPONENT_KEY, pending);
        return new PetInstance(pet.id(), pet.definitionId(), pet.definitionRevision(), components, pet.extensions());
    }
}
