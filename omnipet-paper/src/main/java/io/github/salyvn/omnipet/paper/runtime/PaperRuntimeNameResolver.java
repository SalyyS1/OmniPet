package io.github.salyvn.omnipet.paper.runtime;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.management.PetManagementMetadata;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.paper.text.Displays;

/**
 * The name shown above a rendered pet.
 *
 * <p>Read from the definition's raw node rather than from a field on {@code PetDefinition}. That record is
 * a schema contract with a codec, a migration path, and validation at four layers, and a cosmetic label
 * does not earn a place in it — the same argument the optional {@code behavior} block already settles the
 * same way.
 *
 * <p>Three sources, in falling priority: the name the player gave the pet, the operator's
 * {@code display.name}, and finally the definition ID made readable. The last one is why every active pet
 * now has a plate. Falling through to no name at all meant nameplates only appeared for operators who had
 * discovered an undocumented raw-node key, so in practice they never appeared — which is the "pet được
 * active vẫn chưa có displayname" report. A pet's ID is not a beautiful name, but it is the pet's name, and
 * showing it is what makes the feature visible and the rename worth doing.
 *
 * <p>A player's own name wins. Renaming is the point of {@code PetManagementMetadata.customName}, and a
 * definition-level name that overrode it would make the rename look broken.
 */
final class PaperRuntimeNameResolver {
    /** Where an operator writes the default name: {@code display.name} on the definition. */
    private static final String DISPLAY_NODE = "display";
    private static final String NAME_KEY = "name";

    private PaperRuntimeNameResolver() {}

    /**
     * The pet's name, without its level.
     *
     * <p>The level used to be appended here. It is carried separately on the appearance now, so that the
     * nameplate template decides where it goes and whether it appears at all — an operator who does not
     * want a level writes a template without the placeholder, which was impossible while the two were
     * already one string by the time the plate was composed.
     *
     * @param rawNode the definition's raw node, which may be absent for a pet whose definition is gone
     * @param instance the owned pet, carrying any name its owner gave it
     */
    static String resolve(Map<String, Object> rawNode, PetInstance instance) {
        String name = customName(instance);
        if (name.isEmpty()) name = definitionName(rawNode);
        if (name.isEmpty()) name = readableId(instance);
        if (name.isEmpty()) return "";
        // Truncated rather than rejected: a name too long for a nameplate is a cosmetic problem, and
        // refusing it would cost the pet its render.
        return name.length() <= RendererAppearance.MAX_DISPLAY_NAME
                ? name
                : name.substring(0, RendererAppearance.MAX_DISPLAY_NAME);
    }

    /**
     * The pet's definition ID as something a player can read: {@code ember_fox} becomes {@code Ember fox}.
     *
     * <p>The last resort, so that a pet nobody has named still has a plate. Reuses the same sentence-casing
     * every other player-facing identifier in the plugin goes through, so a definition ID reads the same
     * here as it does in the vault.
     */
    private static String readableId(PetInstance instance) {
        return instance == null ? "" : Displays.identifier(instance.definitionId());
    }

    /** The name the pet's owner gave it, or empty. Never throws: a renderer must not fail on bad data. */
    private static String customName(PetInstance instance) {
        if (instance == null) return "";
        try {
            return PetManagementMetadata.read(instance).customName();
        } catch (RuntimeException ignored) {
            // A malformed management node costs this pet its custom name and nothing else.
            return "";
        }
    }

    /** The operator-authored default from {@code display.name}, or empty. */
    private static String definitionName(Map<String, Object> rawNode) {
        Object display = rawNode == null ? null : rawNode.get(DISPLAY_NODE);
        if (!(display instanceof Map<?, ?> node)) return "";
        return node.get(NAME_KEY) instanceof String name ? name.trim() : "";
    }

    /**
     * The pet's level for the nameplate, or null when it cannot be read.
     *
     * <p>Its own lenient read rather than {@code PetProgressionProjection.read}, which throws on a
     * malformed component. A bad progression node must cost the pet its level suffix, not its render.
     * A pet with no progression node at all is level 1, matching {@code ProgressionState.initial} and the
     * vault's reader.
     */
    static Integer level(PetInstance instance) {
        if (instance == null) return null;
        Object raw = instance.rawComponents().get("progression");
        if (!(raw instanceof Map<?, ?> progression)) return 1;
        if (!(progression.get("level") instanceof Number number)) return null;
        double decimal = number.doubleValue();
        long exact = number.longValue();
        if (!Double.isFinite(decimal) || decimal != exact) return null;
        if (exact < 1 || exact > Integer.MAX_VALUE) return null;
        return (int) exact;
    }
}
