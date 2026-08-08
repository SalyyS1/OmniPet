package io.github.salyvn.omnipet.paper.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.skill.SkillTrigger;

/**
 * Invariants of the trigger listener that only a running server could otherwise show.
 *
 * <p>Every one of these is a way for a skill to fire twice, or not at all, from one player action. None is
 * observable without Bukkit's event bus, and all of them read as "the cooldown is wrong" or "the skill is
 * unreliable" rather than as the wiring mistake they are — so they are asserted against the source.
 */
class SkillTriggerListenerContractTest {
    private static final Path LISTENER = Path.of(
            "src/main/java/io/github/salyvn/omnipet/paper/skill/SkillTriggerListener.java");

    /**
     * A click fires {@code PlayerInteractEvent} once per hand. Casting on both would halve every configured
     * cooldown, and the halving would be invisible in the config.
     */
    @Test
    void anInteractIsReadFromTheMainHandOnly() throws IOException {
        assertTrue(source().contains("EquipmentSlot.HAND"),
                "an interact fires once per hand; without this filter one click casts twice");
    }

    /**
     * {@code EntityDamageByEntityEvent} is a subclass of {@code EntityDamageEvent}, so both handlers see the
     * same hit. Firing {@code ON_DAMAGE_TAKEN} in both would double every cast from being attacked — the
     * most common passive trigger there is.
     */
    @Test
    void damageFromAnEntityIsNotCountedTwice() throws IOException {
        assertTrue(source().contains("!(event instanceof EntityDamageByEntityEvent)"),
                "the generic damage handler also sees entity damage; without this guard both fire");
    }

    /**
     * A toggle event fires on the press and on the release. Casting on both would make one crouch two casts,
     * and the second would land while the player was already standing.
     */
    @Test
    void toggleTriggersFireOnTheStartOfTheActionOnly() throws IOException {
        String source = source();
        assertTrue(source.contains("if (event.isSneaking())"), "a sneak toggle fires on press and release");
        assertTrue(source.contains("if (event.isSprinting())"), "a sprint toggle fires on press and release");
    }

    /**
     * Cancelling the drop and the swap is only correct when a binding actually wants that key. An
     * unconditional cancel would take an item out of every player's hands whether or not they own a pet.
     */
    @Test
    void aVanillaActionIsCancelledOnlyWhenABindingWantsThatKey() throws IOException {
        String source = source();
        for (String trigger : new String[] {"DROP_KEY", "SWAP_HAND_KEY"}) {
            assertTrue(source.contains("hasTrigger(event.getPlayer().getUniqueId(), SkillTrigger." + trigger + ")"),
                    trigger + " must be checked before the vanilla action is cancelled");
        }
    }

    /**
     * A player who logs out must not leave an entry behind. Both the listener's low-health latch and the
     * controller's trigger index are keyed by owner, and neither has any other eviction.
     */
    @Test
    void perOwnerStateIsEvictedOnQuit() throws IOException {
        assertTrue(source().contains("PlayerQuitEvent"),
                "the low-health latch is keyed by owner and has no other eviction");
        String lifecycle = read(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/skill/SkillOwnerLifecycleListener.java"));
        assertTrue(lifecycle.contains("skills.forget("),
                "the controller's trigger index is keyed by owner and has no other eviction");
    }

    /** Every trigger the enum declares should have a handler, or the enum is promising what it cannot do. */
    @Test
    void everyDeclaredTriggerIsDispatchedFromSomewhere() throws IOException {
        String dispatchers = source()
                + read(Path.of("src/main/java/io/github/salyvn/omnipet/paper/skill/SkillIntervalTicker.java"))
                + read(Path.of("src/main/java/io/github/salyvn/omnipet/paper/skill/PaperActiveSkillController.java"));
        for (SkillTrigger trigger : SkillTrigger.values()) {
            assertTrue(dispatchers.contains("SkillTrigger." + trigger.name()),
                    trigger.name() + " is declared but nothing ever fires it");
        }
    }

    /** The listener must not read player state on the main thread; that is what the index is for. */
    @Test
    void theListenerNeverTouchesThePlayerRepository() throws IOException {
        String source = source().toLowerCase(Locale.ROOT);
        assertFalse(source.contains("playerstaterepository"),
                "an event handler that reads disk stalls the server tick");
        assertFalse(source.contains("skillbindingprojection"),
                "reading definitions per event belongs on the controller's queue, not in a handler");
    }

    /**
     * The catalog must follow {@code /mm reload}, not just plugin enable and disable.
     *
     * <p>A reload re-reads every skill file without disabling MythicMobs, so no {@code PluginEnableEvent}
     * arrives. A catalog probed only at startup then stays frozen for the whole uptime, and a skill missing
     * from the catalog is refused before MythicMobs is ever asked — which from a player's seat is a skill
     * that does nothing. Only a running server with MythicMobs installed shows this, so it is asserted here.
     */
    @Test
    void theSkillCatalogFollowsAMythicMobsReload() throws IOException {
        String source = read(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/skill/MythicMobsSkillLifecycleListener.java"));
        assertTrue(source.contains("MythicReloadedEvent"),
                "a /mm reload leaves the catalog stale unless the vendor's own reload event is observed");
        assertTrue(source.contains("MythicLoadedEvent"),
                "the first vendor load must refresh the catalog as well");
        // Registered by name against the vendor loader: a typed handler would link MythicMobs classes into
        // OmniPet's bytecode, and the plugin is built to run with MythicMobs absent.
        assertTrue(source.contains("Class.forName"),
                "the vendor event must be resolved reflectively, not linked");
        assertTrue(read(Path.of("src/main/java/io/github/salyvn/omnipet/paper/OmniPetPlugin.java"))
                        .contains("hookVendorReloads()"),
                "the reload hook is inert unless the plugin attaches it at startup");
        // A disable and re-enable hands MythicMobs a new class loader, so its reload event becomes a
        // different Class and the old registration can never fire. Re-attaching needs the latch cleared.
        assertTrue(source.contains("vendorHooked = false"),
                "a re-enabled MythicMobs must be re-hooked, or reloads stop being observed for the uptime");
    }

    private static String source() throws IOException {
        return read(LISTENER);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
