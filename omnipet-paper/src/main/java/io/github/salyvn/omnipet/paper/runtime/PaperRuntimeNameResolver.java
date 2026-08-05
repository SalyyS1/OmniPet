package io.github.salyvn.omnipet.paper.runtime;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.management.PetManagementMetadata;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;

/**
 * The name shown above a rendered pet.
 *
 * <p>Read from the definition's raw node rather than from a field on {@code PetDefinition}. That record is
 * a schema contract with a codec, a migration path, and validation at four layers, and a cosmetic label
 * does not earn a place in it — the same argument the optional {@code behavior} block already settles the
 * same way. A definition with no name simply has no nameplate.
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
     * The nameplate text for one pet, or empty when it should have none.
     *
     * @param rawNode the definition's raw node, which may be absent for a pet whose definition is gone
     * @param instance the owned pet, carrying any name its owner gave it
     * @param level the pet's level, appended when it is known
     */
    static String resolve(Map<String, Object> rawNode, PetInstance instance, Integer level) {
        String name = customName(instance);
        if (name.isEmpty()) name = definitionName(rawNode);
        if (name.isEmpty()) return "";
        String withLevel = level == null ? name : name + "  Lv." + level;
        // Truncated rather than rejected: a name too long for a nameplate is a cosmetic problem, and
        // refusing it would cost the pet its render.
        return withLevel.length() <= RendererAppearance.MAX_DISPLAY_NAME
                ? withLevel
                : withLevel.substring(0, RendererAppearance.MAX_DISPLAY_NAME);
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
