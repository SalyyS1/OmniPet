package io.github.salyvn.omnipet.paper.progression;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Entity;

/**
 * The MythicMobs internal name of a killed mob, or nothing when it is an ordinary one.
 *
 * <p>Reflective for the same reason every other vendor bridge here is: MythicMobs is optional, its classes
 * are absent on a server without it, and a direct import would make OmniPet fail to load rather than run
 * without the integration. The build forbids vendor imports in {@code omnipet-core} outright and this stays
 * on the Paper side of that line.
 *
 * <p>Fails quiet and stays failed. A server killing hundreds of mobs a second must not pay a reflective
 * exception per kill, so the first failure disables the bridge and every later kill is treated as vanilla —
 * which is the correct fallback anyway, since a vanilla type is what such a mob actually is.
 */
public final class ReflectiveMythicMobsIdentity {
    private final BooleanSupplier pluginEnabled;
    private Method getApiHelper;
    private Method isMythicMob;
    private Method getInstance;
    private Method getMobType;
    private Method instance;
    private String unavailable;

    public ReflectiveMythicMobsIdentity(BooleanSupplier pluginEnabled) {
        this.pluginEnabled = java.util.Objects.requireNonNull(pluginEnabled, "MythicMobs enabled check");
    }

    /**
     * The mob's MythicMobs name, or null when it is not one.
     *
     * <p>Null rather than the vanilla type, so the caller can tell "not a MythicMobs mob" from "a MythicMobs
     * mob whose name happens to match a vanilla type" — those want different config entries.
     */
    public String mythicNameOf(Entity entity) {
        if (entity == null || unavailable != null || !pluginEnabled.getAsBoolean()) return null;
        try {
            bind(entity.getClass().getClassLoader());
            Object api = instance.invoke(null);
            Object helper = getApiHelper.invoke(api);
            if (!(isMythicMob.invoke(helper, entity) instanceof Boolean mythic) || !mythic) return null;
            Object active = getInstance.invoke(helper, entity);
            if (active == null) return null;
            return getMobType.invoke(active) instanceof String name && !name.isBlank() ? name.trim() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            unavailable = detail(failure);
            return null;
        }
    }

    /** Why MythicMobs kill identification is off, or null when it is working. */
    public String unavailableDetail() {
        return unavailable;
    }

    /** Re-arms the bridge after MythicMobs is enabled or reloaded. */
    public synchronized void refresh() {
        unavailable = null;
        instance = null;
    }

    private synchronized void bind(ClassLoader loader) throws ReflectiveOperationException {
        if (instance != null) return;
        Class<?> bukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, loader);
        Class<?> helper = Class.forName("io.lumine.mythic.bukkit.BukkitAPIHelper", true, loader);
        Class<?> active = Class.forName("io.lumine.mythic.core.mobs.ActiveMob", true, loader);
        getApiHelper = bukkit.getMethod("getAPIHelper");
        isMythicMob = helper.getMethod("isMythicMob", Entity.class);
        getInstance = helper.getMethod("getMythicMobInstance", Entity.class);
        // getMobType rather than getType().getInternalName(): it returns the internal name directly, so
        // this binds one method instead of two and never has to handle a null MythicMob in between.
        getMobType = active.getMethod("getMobType");
        instance = bukkit.getMethod("inst");
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
