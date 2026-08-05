package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Which blocks a placed egg can be, and what vanilla does to them.
 *
 * <p>The egg block is whatever material the egg item was configured with, so an operator can point a
 * resource pack at it. That freedom is why this is a predicate rather than one constant: the shipped
 * default is {@code TURTLE_EGG}, and by the time a record is being cleaned up the item's configuration may
 * have changed under it.
 *
 * <p>{@code TURTLE_EGG} is the default because it is the only vanilla block that reads as a clutch of eggs
 * on the ground. It also carries behaviour nobody wants here: <strong>an entity walking over a turtle egg
 * destroys it</strong>, and that arrived as a plain block change rather than a break — so nothing returned
 * the egg and nothing deleted the record. The countdown ran on against a block that no longer existed and
 * the egg became unreclaimable. That is what the material predicates here let the listener guard.
 */
final class PlacedEggBlocks {
    /**
     * Materials a placed egg may legitimately be.
     *
     * <p>Kept to the egg-shaped blocks rather than accepting anything. The set decides whether a block at a
     * recorded position is still <em>our</em> egg, and a permissive check would let cleanup delete whatever
     * a player had since put in its place.
     */
    private static final Set<Material> EGG_MATERIALS = Collections.unmodifiableSet(EnumSet.of(
            Material.TURTLE_EGG,
            Material.DRAGON_EGG,
            Material.SNIFFER_EGG));

    private PlacedEggBlocks() {}

    /** Whether this material is one an OmniPet egg is placed as. */
    static boolean eggMaterial(Material material) {
        return material != null && EGG_MATERIALS.contains(material);
    }

    /**
     * Whether vanilla destroys this material when something stands on it.
     *
     * <p>Only turtle eggs, which is the shipped default. Deliberately narrower than {@link #eggMaterial}:
     * blanket-cancelling every interaction with every egg-shaped block would refuse things vanilla allows
     * and that no report asked about.
     */
    static boolean tramplableMaterial(Material material) {
        return material == Material.TURTLE_EGG;
    }

    /** Whether this block is one an OmniPet egg is placed as. */
    static boolean isEggBlock(Block block) {
        return block != null && eggMaterial(block.getType());
    }

    /** Whether this block is one vanilla destroys when an entity stands on it. */
    static boolean tramplable(Block block) {
        return block != null && tramplableMaterial(block.getType());
    }
}
