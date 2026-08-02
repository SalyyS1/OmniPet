package io.github.salyvn.omnipet.core.skill;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public final class PetSkillStateProjection {
    public static final String COMPONENT_KEY = "skillRuntime";

    private PetSkillStateProjection() {}

    public static PetSkillState read(PetInstance pet) {
        Object raw = pet.rawComponents().get(COMPONENT_KEY);
        if (raw == null) return PetSkillState.empty();
        if (!(raw instanceof Map<?, ?> node)) throw new IllegalArgumentException("pet skill runtime must be a map");
        Map<String, Long> cooldowns = longMap(node.get("cooldowns"));
        Map<UUID, SkillActionReservation> pending = pending(node.get("pending"));
        LinkedHashMap<String, Object> extensions = stringMap(node);
        extensions.remove("cooldowns");
        extensions.remove("pending");
        return new PetSkillState(cooldowns, pending, extensions);
    }

    public static PetInstance write(PetInstance pet, PetSkillState state) {
        LinkedHashMap<String, Object> node = new LinkedHashMap<>(state.extensions());
        node.put("cooldowns", state.cooldownDeadlines());
        LinkedHashMap<String, Object> pending = new LinkedHashMap<>();
        state.pendingActions().forEach((id, reservation) -> pending.put(id.toString(), Map.of(
                "bindingId", reservation.bindingId(),
                "staminaCost", reservation.staminaCost(),
                "cooldownDeadline", reservation.cooldownDeadline(),
                "createdAt", reservation.createdAtEpochMillis(),
                "persistCooldown", reservation.persistCooldown())));
        node.put("pending", pending);
        LinkedHashMap<String, Object> components = new LinkedHashMap<>(pet.rawComponents());
        components.put(COMPONENT_KEY, node);
        return new PetInstance(pet.id(), pet.definitionId(), pet.definitionRevision(), components, pet.extensions());
    }

    private static Map<String, Long> longMap(Object raw) {
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> source)) throw new IllegalArgumentException("skill cooldowns must be a map");
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String binding) || binding.isBlank() || !(value instanceof Number number)
                    || number.longValue() < 0 || number.doubleValue() != number.longValue()) {
                throw new IllegalArgumentException("skill cooldown entry is invalid");
            }
            result.put(binding, number.longValue());
        });
        return Map.copyOf(result);
    }

    private static Map<UUID, SkillActionReservation> pending(Object raw) {
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> source)) throw new IllegalArgumentException("pending skill actions must be a map");
        LinkedHashMap<UUID, SkillActionReservation> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String id) || !(value instanceof Map<?, ?> node)) {
                throw new IllegalArgumentException("pending skill action is invalid");
            }
            UUID actionId = UUID.fromString(id);
            result.put(actionId, new SkillActionReservation(
                    actionId,
                    text(node.get("bindingId")),
                    decimal(node.get("staminaCost")),
                    integer(node.get("cooldownDeadline")),
                    integer(node.get("createdAt")),
                    Boolean.TRUE.equals(node.get("persistCooldown"))));
        });
        return Map.copyOf(result);
    }

    private static LinkedHashMap<String, Object> stringMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(value)));
        return result;
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("skill binding ID is invalid");
        return text;
    }

    private static double decimal(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0) {
            throw new IllegalArgumentException("skill reservation stamina is invalid");
        }
        return number.doubleValue();
    }

    private static long integer(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.longValue() || number.longValue() < 0) {
            throw new IllegalArgumentException("skill reservation time is invalid");
        }
        return number.longValue();
    }
}
