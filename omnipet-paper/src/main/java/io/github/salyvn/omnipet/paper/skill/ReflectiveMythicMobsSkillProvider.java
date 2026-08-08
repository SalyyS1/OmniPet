package io.github.salyvn.omnipet.paper.skill;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import io.github.salyvn.omnipet.core.skill.SkillCastRequest;
import io.github.salyvn.omnipet.core.skill.SkillCastResult;
import io.github.salyvn.omnipet.core.skill.SkillCatalogSnapshot;
import io.github.salyvn.omnipet.core.skill.SkillProvider;
import io.github.salyvn.omnipet.core.skill.SkillProviderHealth;

/** MythicMobs v5 bridge that never links vendor classes into OmniPet bytecode. */
public final class ReflectiveMythicMobsSkillProvider implements SkillProvider {
    public static final String PROVIDER_ID = "MYTHICMOBS";
    private static final String MYTHIC_BUKKIT = "io.lumine.mythic.bukkit.MythicBukkit";

    private final ClassLoader loader;
    private final BooleanSupplier pluginEnabled;
    private final BooleanSupplier mainThread;
    private final Function<UUID, Object> casterResolver;
    private final Function<UUID, Object> targetResolver;
    private volatile State state = State.unavailable(0, "provider has not been probed");

    public ReflectiveMythicMobsSkillProvider(
            ClassLoader loader,
            BooleanSupplier pluginEnabled,
            BooleanSupplier mainThread,
            Function<UUID, Object> casterResolver) {
        this(loader, pluginEnabled, mainThread, casterResolver, id -> null);
    }

    /**
     * @param targetResolver turns a resolved target ID back into a live entity, or null when it has left
     */
    public ReflectiveMythicMobsSkillProvider(
            ClassLoader loader,
            BooleanSupplier pluginEnabled,
            BooleanSupplier mainThread,
            Function<UUID, Object> casterResolver,
            Function<UUID, Object> targetResolver) {
        this.loader = Objects.requireNonNull(loader, "MythicMobs class loader");
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "MythicMobs enabled probe");
        this.mainThread = Objects.requireNonNull(mainThread, "main-thread probe");
        this.casterResolver = Objects.requireNonNull(casterResolver, "skill caster resolver");
        this.targetResolver = Objects.requireNonNull(targetResolver, "skill target resolver");
    }

    public synchronized SkillCatalogSnapshot refresh(long epoch) {
        if (epoch < 0) throw new IllegalArgumentException("provider epoch cannot be negative");
        if (!pluginEnabled.getAsBoolean()) {
            state = State.unavailable(epoch, "MythicMobs is not enabled");
            return state.catalog();
        }
        try {
            Object api = instance();
            Object skillManager = invokeNoArgs(api, "getSkillManager");
            Object rawNames = invokeNoArgs(skillManager, "getSkillNames");
            if (!(rawNames instanceof Collection<?> names)) {
                throw new ReflectiveOperationException("getSkillNames did not return a collection");
            }
            LinkedHashSet<String> skillIds = new LinkedHashSet<>();
            for (Object name : names) {
                if (name instanceof String id && !id.isBlank()) skillIds.add(id);
            }
            state = State.available(epoch, api, skillIds);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            state = State.unavailable(epoch, detail(failure));
        }
        return state.catalog();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public SkillCatalogSnapshot catalog() {
        return state.catalog();
    }

    @Override
    public SkillCastResult cast(SkillCastRequest request) {
        Objects.requireNonNull(request, "skill cast request");
        State observed = state;
        if (!observed.catalog().health().available() || observed.api() == null) {
            return castResult(SkillCastResult.Status.FAILED, observed.catalog().health().detail());
        }
        if (!mainThread.getAsBoolean()) {
            return castResult(SkillCastResult.Status.FAILED, "MythicMobs casts require the Paper main thread");
        }
        Object caster = casterResolver.apply(request.ownerId());
        if (caster == null) {
            return castResult(SkillCastResult.Status.INVALID_TARGET,
                    "skill owner is not available as a living caster");
        }
        try {
            Object helper = invokeNoArgs(observed.api(), "getAPIHelper");
            List<Object> targets = resolveTargets(request);
            if (!targets.isEmpty()) {
                Method targeted = findTargetedCast(helper.getClass());
                if (targeted != null) {
                    Object origin = caster instanceof Entity entity ? entity.getLocation() : null;
                    return outcome(invoke(targeted, helper, caster, request.skillId(), caster, origin,
                            targets, List.of(), (float) request.power()));
                }
            }
            Method cast = findSimpleCast(helper.getClass(), caster.getClass());
            return outcome(invoke(cast, helper, caster, request.skillId()));
        } catch (VendorCastFailure failure) {
            // The skill threw inside MythicMobs, which says this skill is wrong, not that the bridge is.
            // Quarantining here used to take every other skill down with it and stay down until something
            // reloaded the plugin, so one bad mechanic silently ended pet skills for the rest of the uptime.
            return castResult(SkillCastResult.Status.FAILED, detail(failure.getCause()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            quarantine(observed.catalog().epoch(), failure);
            return castResult(SkillCastResult.Status.FAILED, detail(failure));
        }
    }

    private static SkillCastResult outcome(Object result) {
        if (result instanceof Boolean succeeded && !succeeded) {
            return castResult(SkillCastResult.Status.REJECTED, "MythicMobs rejected the skill cast");
        }
        return castResult(SkillCastResult.Status.SUCCESS, "MythicMobs accepted the skill cast");
    }

    /** The still-present entities among the resolved targets, in the order the resolver chose them. */
    private List<Object> resolveTargets(SkillCastRequest request) {
        List<Object> targets = new ArrayList<>();
        for (UUID id : request.entityTargets()) {
            Object entity = targetResolver.apply(id);
            if (entity != null) targets.add(entity);
        }
        return targets;
    }

    /**
     * Calls a vendor method, keeping "the skill failed" apart from "the bridge is broken".
     *
     * <p>Only the second is a reason to quarantine the adapter. A throw from inside {@code castSkill} is
     * reported as {@link InvocationTargetException}, and it means this one skill's own mechanics failed —
     * a missing entity, a bad {@code @Target}, an arithmetic error in a config value. Treating that as an
     * adapter fault disabled every other skill on the server until a plugin reload, which is what made one
     * misconfigured skill look like "pet skills do not work at all".
     */
    private static Object invoke(Method method, Object owner, Object... arguments)
            throws ReflectiveOperationException, VendorCastFailure {
        try {
            return method.invoke(owner, arguments);
        } catch (InvocationTargetException thrown) {
            Throwable cause = thrown.getCause();
            // A linkage error is the bridge's problem: the vendor's own shape is not what was compiled
            // against, and every later cast would fail the same way.
            if (cause == null || cause instanceof LinkageError) throw thrown;
            throw new VendorCastFailure(cause);
        }
    }

    /** A skill that threw inside MythicMobs. Scoped to this cast, never to the adapter. */
    private static final class VendorCastFailure extends Exception {
        private VendorCastFailure(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }

    private Object instance() throws ReflectiveOperationException {
        Class<?> type = Class.forName(MYTHIC_BUKKIT, true, loader);
        Method method = type.getMethod("inst");
        return Objects.requireNonNull(method.invoke(null), "MythicBukkit.inst returned null");
    }

    private synchronized void quarantine(long epoch, Throwable failure) {
        if (state.catalog().epoch() == epoch) state = State.quarantined(epoch, "adapter quarantined: " + detail(failure));
    }

    /**
     * {@code castSkill(caster, skill, trigger, origin, entityTargets, locationTargets, power)}.
     *
     * <p>The two-argument overload leaves the choice of target to MythicMobs, which picks from the caster —
     * and the caster is the owning <em>player</em>, so a pet's attack skill aimed at whatever the player's
     * own targeting rules found. This overload is how a pet hits what the pet is supposed to hit. Null when
     * the installed MythicMobs does not offer it, so an older build still casts, just untargeted.
     */
    private static Method findTargetedCast(Class<?> helperType) {
        for (Method method : helperType.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals("castSkill") && parameters.length == 7
                    && parameters[1] == String.class
                    && parameters[3] == Location.class
                    && Collection.class.isAssignableFrom(parameters[4])
                    && Collection.class.isAssignableFrom(parameters[5])
                    && parameters[6] == float.class) {
                return method;
            }
        }
        return null;
    }

    private static Method findSimpleCast(Class<?> helperType, Class<?> casterType) throws NoSuchMethodException {
        for (Method method : helperType.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals("castSkill") && parameters.length == 2
                    && parameters[0].isAssignableFrom(casterType) && parameters[1] == String.class) {
                return method;
            }
        }
        throw new NoSuchMethodException("compatible BukkitAPIHelper.castSkill(caster, String) was not found");
    }

    private static Object invokeNoArgs(Object owner, String method) throws ReflectiveOperationException {
        try {
            return owner.getClass().getMethod(method).invoke(owner);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof LinkageError linkage) throw linkage;
            throw failure;
        }
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        String value = message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private static SkillCastResult castResult(SkillCastResult.Status status, String detail) {
        return new SkillCastResult(status, detail);
    }

    private record State(SkillCatalogSnapshot catalog, Object api) {
        static State available(long epoch, Object api, Set<String> skills) {
            return new State(new SkillCatalogSnapshot(
                    epoch, new SkillProviderHealth(
                            SkillProviderHealth.Status.AVAILABLE, "MythicMobs v5 API ready"), skills), api);
        }

        static State unavailable(long epoch, String reason) {
            return new State(new SkillCatalogSnapshot(
                    epoch, new SkillProviderHealth(SkillProviderHealth.Status.UNAVAILABLE, reason), Set.of()), null);
        }

        static State quarantined(long epoch, String reason) {
            return new State(new SkillCatalogSnapshot(
                    epoch, new SkillProviderHealth(SkillProviderHealth.Status.QUARANTINED, reason), Set.of()), null);
        }
    }
}
