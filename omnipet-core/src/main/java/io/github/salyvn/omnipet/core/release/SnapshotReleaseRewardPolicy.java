package io.github.salyvn.omnipet.core.release;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** Reads the immutable release policy snapshotted when the pet was hatched. */
public final class SnapshotReleaseRewardPolicy implements ReleaseRewardPolicy {
    @Override
    public ReleaseRewardBundle calculate(PetInstance pet) {
        Object raw = pet.rawComponents().get("release");
        if (raw == null) return new ReleaseRewardBundle(List.of(), List.of());
        Map<String, Object> release = map(raw, "release");
        Object rewardsRaw = release.get("rewards");
        if (rewardsRaw == null) return new ReleaseRewardBundle(List.of(), List.of());
        Map<String, Object> rewards = map(rewardsRaw, "release.rewards");
        List<ReleaseRewardBundle.InternalReward> internal = new ArrayList<>();
        List<ReleaseRewardBundle.ExternalReward> external = new ArrayList<>();
        readMaterials(rewards.get("materials"), internal);
        readCurrencies(rewards.get("currency"), external);
        readInternal(rewards.get("internal"), internal);
        readExternal(rewards.get("external"), external);
        return new ReleaseRewardBundle(internal, external);
    }

    private static void readMaterials(
            Object raw,
            List<ReleaseRewardBundle.InternalReward> rewards) {
        if (raw == null) return;
        map(raw, "release.rewards.materials").forEach((material, amount) -> rewards.add(
                new ReleaseRewardBundle.InternalReward(
                        "material", positiveLong(amount, "release material amount"),
                        Map.of("material", required(material, "release material")))));
    }

    private static void readCurrencies(
            Object raw,
            List<ReleaseRewardBundle.ExternalReward> rewards) {
        if (raw == null) return;
        map(raw, "release.rewards.currency").forEach((provider, amount) -> rewards.add(
                new ReleaseRewardBundle.ExternalReward(
                        required(provider, "release currency provider").toLowerCase(Locale.ROOT),
                        "currency", positiveDecimal(amount, "release currency amount"), Map.of())));
    }

    private static void readInternal(
            Object raw,
            List<ReleaseRewardBundle.InternalReward> rewards) {
        if (raw == null) return;
        for (Map<String, Object> node : list(raw, "release.rewards.internal")) {
            String id = required(node.getOrDefault("rewardId", node.get("id")), "internal reward ID");
            long amount = positiveLong(node.get("amount"), "internal reward amount");
            Map<String, Object> payload = node.get("payload") == null
                    ? Map.of()
                    : map(node.get("payload"), "internal reward payload");
            rewards.add(new ReleaseRewardBundle.InternalReward(id, amount, payload));
        }
    }

    private static void readExternal(
            Object raw,
            List<ReleaseRewardBundle.ExternalReward> rewards) {
        if (raw == null) return;
        for (Map<String, Object> node : list(raw, "release.rewards.external")) {
            String provider = required(node.get("provider"), "external reward provider");
            String id = required(node.getOrDefault("rewardId", node.get("id")), "external reward ID");
            BigDecimal amount = positiveDecimal(node.get("amount"), "external reward amount");
            Map<String, Object> payload = node.get("payload") == null
                    ? Map.of()
                    : map(node.get("payload"), "external reward payload");
            rewards.add(new ReleaseRewardBundle.ExternalReward(provider, id, amount, payload));
        }
    }

    private static List<Map<String, Object>> list(Object raw, String label) {
        if (!(raw instanceof List<?> values)) throw new IllegalArgumentException(label + " must be a list");
        List<Map<String, Object>> result = new ArrayList<>(values.size());
        for (Object value : values) result.add(map(value, label + " entry"));
        return List.copyOf(result);
    }

    private static Map<String, Object> map(Object raw, String label) {
        if (!(raw instanceof Map<?, ?> values)) throw new IllegalArgumentException(label + " must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(value)));
        return result;
    }

    private static String required(Object value, String label) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return text.trim();
    }

    private static long positiveLong(Object value, String label) {
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.longValue()
                || number.longValue() < 1) {
            throw new IllegalArgumentException(label + " must be a positive integer");
        }
        return number.longValue();
    }

    private static BigDecimal positiveDecimal(Object value, String label) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be a decimal", invalid);
        }
        if (amount.signum() <= 0) throw new IllegalArgumentException(label + " must be positive");
        return amount;
    }
}
