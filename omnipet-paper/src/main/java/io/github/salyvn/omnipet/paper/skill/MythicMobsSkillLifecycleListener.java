package io.github.salyvn.omnipet.paper.skill;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps the skill catalog in step with MythicMobs.
 *
 * <p>Enabling and disabling the plugin are the obvious boundaries, and the two are not enough on their own:
 * {@code /mm reload} re-reads every skill file <em>without</em> disabling the plugin, so no
 * {@link PluginEnableEvent} arrives and a catalog probed at startup stays frozen for the rest of the uptime.
 * A skill added or renamed after that reload is not in the catalog, and a skill that is not in the catalog is
 * refused before MythicMobs is ever asked — which from a player's seat is a skill that does nothing at all.
 *
 * <p>MythicMobs announces its own reload, so that is what is listened to. The event types are resolved by
 * name against the MythicMobs class loader and registered with an executor rather than an annotated method,
 * because a typed handler would link vendor classes into OmniPet's bytecode and the plugin is deliberately
 * built to run without them present.
 */
public final class MythicMobsSkillLifecycleListener implements Listener {
    /**
     * What MythicMobs fires once its own configuration has been read.
     *
     * <p>Both are tried and neither is required. {@code MythicReloadedEvent} is the one that matters and is
     * present across builds; {@code MythicLoadedEvent} covers the first load and is absent from 5.7.x and
     * 5.8.x, which were checked directly. Whichever exists is attached, so the catalog follows a reload on
     * old and new builds alike without either name being a hard dependency.
     */
    private static final List<String> VENDOR_LOAD_EVENTS = List.of(
            "io.lumine.mythic.bukkit.events.MythicLoadedEvent",
            "io.lumine.mythic.bukkit.events.MythicReloadedEvent");

    private final PaperMythicMobsSkillContext context;
    private final JavaPlugin plugin;
    private final Consumer<String> warnings;
    private boolean vendorHooked;

    public MythicMobsSkillLifecycleListener(PaperMythicMobsSkillContext context) {
        this(context, null, warning -> {});
    }

    /**
     * @param plugin the owning plugin, needed to register the vendor's own reload event; null keeps only the
     *     enable and disable boundaries, which is what the contract tests exercise
     */
    public MythicMobsSkillLifecycleListener(
            PaperMythicMobsSkillContext context, JavaPlugin plugin, Consumer<String> warnings) {
        this.context = Objects.requireNonNull(context, "MythicMobs skill context");
        this.plugin = plugin;
        this.warnings = Objects.requireNonNull(warnings, "warning sink");
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("MythicMobs")) return;
        context.refresh();
        // MythicMobs may have enabled after OmniPet, in which case its event classes were not loadable when
        // this listener was registered and the reload hook could not be attached yet.
        hookVendorReloads();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("MythicMobs")) return;
        context.refresh();
        // The hook is dropped rather than kept. A disable and re-enable — PlugMan, or a vendor update in
        // place — gives MythicMobs a new class loader, so its reload event becomes a different Class and the
        // registration made against the old one can never fire again. Clearing the latch means the next
        // enable attaches to the classes that are actually loaded.
        vendorHooked = false;
    }

    /**
     * Attaches to MythicMobs' own load and reload events, once.
     *
     * <p>Safe to call whenever MythicMobs might have appeared: it does nothing when the plugin is absent, and
     * nothing on a second call. Failure to attach is reported rather than thrown — a server that cannot get
     * the hook still casts skills, it just needs an OmniPet reload to notice new ones.
     */
    public void hookVendorReloads() {
        if (vendorHooked || plugin == null) return;
        Plugin mythic = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        if (mythic == null || !mythic.isEnabled()) return;
        int attached = 0;
        for (String name : VENDOR_LOAD_EVENTS) {
            if (hook(name, mythic)) attached++;
        }
        // Silent when at least one attached. Neither event is present in every build — MythicMobs 5.7.x and
        // 5.8.x ship MythicReloadedEvent and no MythicLoadedEvent — so warning per absent class would put
        // "reload hook is unavailable" in the log of a server whose reloads are in fact observed, which reads
        // as exactly the breakage this hook exists to prevent.
        if (attached > 0) {
            vendorHooked = true;
            return;
        }
        // Nothing attached: reloads genuinely go unnoticed, and new skills need an OmniPet reload to appear.
        // Not latched, so the next enable tries again.
        warnings.accept("MythicMobs reload events are unavailable on this build ("
                + String.join(", ", VENDOR_LOAD_EVENTS)
                + "); a skill added by /mm reload needs an OmniPet reload before it can be cast");
    }

    private boolean hook(String eventName, Plugin mythic) {
        try {
            Class<?> type = Class.forName(eventName, false, mythic.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(type)) return false;
            plugin.getServer().getPluginManager().registerEvent(
                    type.asSubclass(Event.class), this, EventPriority.MONITOR,
                    (ignored, event) -> {
                        if (type.isInstance(event)) refreshOnMainThread();
                    },
                    plugin);
            return true;
        } catch (ClassNotFoundException | RuntimeException | LinkageError absent) {
            return false;
        }
    }

    /**
     * Re-probes the catalog on the main thread.
     *
     * <p>The refresh asserts the main thread, and MythicMobs does part of its loading off it, so a reload
     * announced from a worker would otherwise turn a recoverable staleness into a thrown listener.
     */
    private void refreshOnMainThread() {
        if (Bukkit.isPrimaryThread()) {
            context.refresh();
            return;
        }
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, context::refresh);
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
