package io.github.salyvn.omnipet.paper.progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import io.github.salyvn.omnipet.core.progression.KillExperienceRules;
import io.github.salyvn.omnipet.core.progression.PendingExperienceLedger;

/**
 * A killed mob turns into experience for the killer's active pets.
 *
 * <p>Does as little as possible on the main thread, because this fires on every mob death on the server. It
 * reads the killer, asks the rules what the kill is worth, and banks the number. Nothing here touches disk,
 * takes a lock, or calls a repository — the ledger is flushed on a timer by {@link KillExperienceFlusher}.
 *
 * <p>Which pets are active is read from a snapshot supplied by the runtime rather than from the player's
 * file, for the same reason: a disk read per kill would be worse than the write it was meant to avoid.
 *
 * <p>The mob's identity is its MythicMobs internal name when it has one, and its vanilla type otherwise, so
 * an operator writes {@code SkeletalKnight} or {@code ZOMBIE} in the same map and neither needs a prefix.
 */
public final class KillExperienceListener implements Listener {
    private final PendingExperienceLedger ledger;
    private final ReflectiveMythicMobsIdentity mythicMobs;
    private final java.util.function.Supplier<KillExperienceRules> rules;
    private final java.util.function.Function<UUID, List<UUID>> activePets;

    /**
     * @param rules read per kill rather than captured, so a reload takes effect without re-registering
     * @param activePets the pets that owner currently has out, in the order the runtime holds them
     */
    public KillExperienceListener(
            PendingExperienceLedger ledger,
            ReflectiveMythicMobsIdentity mythicMobs,
            java.util.function.Supplier<KillExperienceRules> rules,
            java.util.function.Function<UUID, List<UUID>> activePets) {
        this.ledger = Objects.requireNonNull(ledger, "pending experience ledger");
        this.mythicMobs = Objects.requireNonNull(mythicMobs, "MythicMobs identity");
        this.rules = Objects.requireNonNull(rules, "kill experience rules");
        this.activePets = Objects.requireNonNull(activePets, "active pet lookup");
    }

    /**
     * Banks experience for a kill.
     *
     * <p>{@code MONITOR} and {@code ignoreCancelled}: this only observes, and reading a death another
     * plugin cancelled would credit a kill that did not happen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        KillExperienceRules current = rules.get();
        // Before anything else, so a server that has not enabled this pays one boolean per death.
        if (!current.enabled()) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        List<UUID> pets = activePets.apply(killer.getUniqueId());
        if (pets == null || pets.isEmpty()) return;

        double each = current.experienceFor(mobId(event.getEntity()), pets.size());
        if (each <= 0) return;
        for (UUID petId : pets) ledger.add(killer.getUniqueId(), petId, each);
    }

    /**
     * What the rules match against: the MythicMobs name when there is one, else the vanilla type.
     *
     * <p>MythicMobs first because one of its mobs is also a vanilla entity underneath — a custom boss built
     * on a zombie would otherwise be paid as a zombie, and an operator could never price it separately.
     */
    private String mobId(Entity entity) {
        String mythic = mythicMobs.mythicNameOf(entity);
        return mythic != null ? mythic : entity.getType().name();
    }

    /** The banked amounts, grouped by owner, for the flusher to write. */
    public Map<UUID, Map<UUID, Double>> drain() {
        Map<UUID, Map<UUID, Double>> drained = ledger.drain();
        return drained.isEmpty() ? Map.of() : new LinkedHashMap<>(drained);
    }
}
