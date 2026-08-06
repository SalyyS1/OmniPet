package io.github.salyvn.omnipet.paper.buff;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;

/** Applies only OmniPet-owned stable modifiers through the MythicLib 1.7.1 API shape. */
public final class ReflectiveMythicLibBuffPort {
    private final BooleanSupplier mainThread;
    private final Predicate<UUID> ownerOnline;
    private final Bindings bindings;
    private final Map<UUID, Set<Installed>> installed = new LinkedHashMap<>();
    private String unavailableReason;

    public ReflectiveMythicLibBuffPort(
            ClassLoader providerLoader,
            BooleanSupplier mainThread,
            Predicate<UUID> ownerOnline) {
        this.mainThread = Objects.requireNonNull(mainThread, "main-thread probe");
        this.ownerOnline = Objects.requireNonNull(ownerOnline, "online-owner probe");
        Bindings resolved = null;
        String failure = null;
        try {
            resolved = Bindings.load(Objects.requireNonNull(providerLoader, "MythicLib class loader"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failure = detail(error);
        }
        this.bindings = resolved;
        this.unavailableReason = failure;
    }

    public synchronized MythicLibBuffResult reconcile(UUID ownerId, List<PetStatBuff> desired) {
        Objects.requireNonNull(ownerId, "buff owner ID");
        desired = List.copyOf(desired == null ? List.of() : desired);
        if (!mainThread.getAsBoolean()) return result(MythicLibBuffResult.Status.OFF_THREAD, 0, 0, 0,
                "MythicLib stat mutation requires the Paper main thread");
        if (bindings == null || unavailableReason != null) return result(MythicLibBuffResult.Status.UNAVAILABLE, 0, 0,
                desired.size(), unavailableReason == null ? "MythicLib is unavailable" : unavailableReason);
        if (!ownerOnline.test(ownerId)) return result(MythicLibBuffResult.Status.UNAVAILABLE, 0, 0, desired.size(),
                "buff owner is offline");
        try {
            Object statMap = bindings.statMap(ownerId);
            if (statMap == null) {
                // MythicLib has no data loaded for this player yet — it loads on its own join handler, and
                // ours can run first. Not a quarantine: the adapter is fine, this one owner is early. The
                // next reconcile (activation, respawn, or a vault change) picks them up.
                return result(MythicLibBuffResult.Status.UNAVAILABLE, 0, 0, desired.size(),
                        "MythicLib player data is not loaded yet");
            }
            Set<Installed> next = new LinkedHashSet<>();
            int applied = 0;
            int removed = 0;
            int skipped = 0;
            for (PetStatBuff buff : desired) {
                Installed key = Installed.of(ownerId, buff);
                Object instance = bindings.statInstance(statMap, buff.statId());
                // Null-checked for safety, but this is not how an unknown stat shows up: MythicLib's
                // getInstance is a computeIfAbsent, so it mints a live instance for any name at all. An ID
                // it has never registered therefore installs successfully onto a stat nothing reads — which
                // is exactly how pet stats came to do nothing while reporting success. Detection lives in
                // knownStat below instead, against the registry.
                if (instance == null) {
                    skipped++;
                    continue;
                }
                if (!bindings.knownStat(buff.statId())) {
                    // Counted as skipped and NOT installed. Installing onto an orphan instance is what made
                    // the failure invisible; refusing it is what lets the coordinator warn about it.
                    skipped++;
                    continue;
                }
                if (bindings.modifier(instance, key.modifierId()) != null) {
                    bindings.remove(instance, key.modifierId());
                    removed++;
                }
                if (buff.value() != 0) {
                    bindings.register(instance, bindings.modifier(key.modifierId(), buff));
                    next.add(key);
                    applied++;
                }
            }
            for (Installed stale : difference(installed.getOrDefault(ownerId, Set.of()), next)) {
                Object instance = bindings.statInstance(statMap, stale.statId());
                if (instance != null && bindings.modifier(instance, stale.modifierId()) != null) {
                    bindings.remove(instance, stale.modifierId());
                    removed++;
                }
            }
            if (next.isEmpty()) installed.remove(ownerId);
            else installed.put(ownerId, Set.copyOf(next));
            return result(MythicLibBuffResult.Status.APPLIED, applied, removed, skipped, "owner buffs reconciled");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            unavailableReason = "adapter quarantined: " + detail(failure);
            return result(MythicLibBuffResult.Status.QUARANTINED, 0, 0, desired.size(), unavailableReason);
        }
    }

    public MythicLibBuffResult clear(UUID ownerId) {
        return reconcile(ownerId, List.of());
    }

    private static Set<Installed> difference(Set<Installed> existing, Set<Installed> next) {
        LinkedHashSet<Installed> stale = new LinkedHashSet<>(existing);
        stale.removeAll(next);
        return stale;
    }

    private static MythicLibBuffResult result(
            MythicLibBuffResult.Status status, int applied, int removed, int skipped, String detail) {
        return new MythicLibBuffResult(status, applied, removed, skipped, detail);
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        String value = message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private record Installed(UUID modifierId, String statId) {
        static Installed of(UUID ownerId, PetStatBuff buff) {
            String identity = "omnipet:" + ownerId + ':' + buff.petInstanceId() + ':'
                    + buff.statId() + ':' + buff.modifierType();
            return new Installed(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)), buff.statId());
        }
    }

    private record Bindings(
            Method playerDataGet,
            Method getStatMap,
            Method getInstance,
            Method getModifier,
            Method removeModifier,
            Method registerModifier,
            Constructor<?> modifierConstructor,
            Class<? extends Enum> modifierType,
            Object equipmentOther,
            Object sourceOther,
            Method mythicLibInstance,
            Method statsOf,
            Method isRegistered) {
        static Bindings load(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> playerData = Class.forName("io.lumine.mythic.lib.api.player.MMOPlayerData", true, loader);
            Class<?> statMap = Class.forName("io.lumine.mythic.lib.api.stat.StatMap", true, loader);
            Class<?> statInstance = Class.forName("io.lumine.mythic.lib.api.stat.StatInstance", true, loader);
            Class<?> modifier = Class.forName("io.lumine.mythic.lib.api.stat.modifier.StatModifier", true, loader);
            Class<? extends Enum> type = enumType(loader, "io.lumine.mythic.lib.player.modifier.ModifierType");
            Class<? extends Enum> equipment = enumType(loader, "io.lumine.mythic.lib.api.player.EquipmentSlot");
            Class<? extends Enum> source = enumType(loader, "io.lumine.mythic.lib.player.modifier.ModifierSource");
            // Optional: the registry lookup is how an unknown stat ID is caught, but losing it must cost
            // the diagnostic rather than the whole adapter. Nulls mean "cannot check", and every stat is
            // then treated as known — the behaviour before this check existed.
            Method mythicLibInstance = null;
            Method statsOf = null;
            Method isRegistered = null;
            try {
                Class<?> mythicLib = Class.forName("io.lumine.mythic.lib.MythicLib", true, loader);
                mythicLibInstance = mythicLib.getMethod("inst");
                statsOf = mythicLib.getMethod("getStats");
                isRegistered = statsOf.getReturnType().getMethod("isRegistered", String.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                mythicLibInstance = null;
                statsOf = null;
                isRegistered = null;
            }
            return new Bindings(
                    // getOrNull, not get. MythicLib's get() throws "Player data not loaded" for a player it
                    // has not set up yet, and this adapter treats a throw as an ABI mismatch and quarantines
                    // itself permanently — for every owner, not just that one. So one player reconciled a
                    // moment too early after a restart would silently disable pet stats server-wide until
                    // the next reload. getOrNull answers null for exactly that case.
                    playerData.getMethod("getOrNull", UUID.class),
                    playerData.getMethod("getStatMap"),
                    statMap.getMethod("getInstance", String.class),
                    statInstance.getMethod("getModifier", UUID.class),
                    statInstance.getMethod("removeModifier", UUID.class),
                    statInstance.getMethod("registerModifier", modifier),
                    modifier.getConstructor(UUID.class, String.class, String.class, double.class,
                            type, equipment, source),
                    type,
                    Enum.valueOf(equipment, "OTHER"),
                    Enum.valueOf(source, "OTHER"),
                    mythicLibInstance,
                    statsOf,
                    isRegistered);
        }

        /** The owner's stat map, or null when MythicLib has not loaded their data yet. */
        Object statMap(UUID ownerId) throws ReflectiveOperationException {
            Object data = playerDataGet.invoke(null, ownerId);
            if (data == null) return null;
            return getStatMap.invoke(data);
        }

        /**
         * Whether MythicLib actually registered this stat.
         *
         * <p>The only way to catch a stat ID MythicLib has never heard of. Its {@code getInstance} is a
         * {@code computeIfAbsent}, so it answers every name with a live instance and a modifier installs
         * happily onto a stat nothing reads — success, reported, invisible, no effect.
         *
         * <p>Answers true when the registry cannot be reached, so a vendor ABI change costs the diagnostic
         * rather than every pet's stats.
         */
        boolean knownStat(String statId) {
            if (mythicLibInstance == null || statsOf == null || isRegistered == null) return true;
            try {
                Object instance = mythicLibInstance.invoke(null);
                if (instance == null) return true;
                Object manager = statsOf.invoke(instance);
                if (manager == null) return true;
                return Boolean.TRUE.equals(isRegistered.invoke(manager, statId));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return true;
            }
        }

        Object statInstance(Object map, String stat) throws ReflectiveOperationException {
            return getInstance.invoke(map, stat);
        }

        Object modifier(Object instance, UUID id) throws ReflectiveOperationException {
            return getModifier.invoke(instance, id);
        }

        void remove(Object instance, UUID id) throws ReflectiveOperationException {
            removeModifier.invoke(instance, id);
        }

        void register(Object instance, Object modifier) throws ReflectiveOperationException {
            registerModifier.invoke(instance, modifier);
        }

        Object modifier(UUID id, PetStatBuff buff) throws ReflectiveOperationException {
            Object type = Enum.valueOf(modifierType, buff.modifierType());
            return modifierConstructor.newInstance(id, "OmniPet " + buff.statId(), buff.statId(), buff.value(),
                    type, equipmentOther, sourceOther);
        }

        @SuppressWarnings("unchecked")
        private static Class<? extends Enum> enumType(ClassLoader loader, String name) throws ClassNotFoundException {
            return (Class<? extends Enum>) Class.forName(name, true, loader).asSubclass(Enum.class);
        }
    }
}
