package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * What each Studio chat field expects, and the prompt block that says so.
 *
 * <p>{@code PetStudioController.awaitField} used to close the inventory and wait for chat without
 * sending anything, so the operator was given no format, no example, and no way to know that
 * {@code cancel} works. Every await path now sends one of these before it starts capturing.
 */
enum StudioFieldPrompt {
    ICON("icon.head", "Head icon",
            "a texture URL, a 64-character texture hash, a pasted base64 payload, "
                    + "or <TEXTURE_URL|BASE64|HEAD_CATALOG> <value>",
            "https://textures.minecraft.net/texture/<hash>"),
    DISPLAY("display", "Renderer",
            "<HEAD|MODELENGINE> [model-id]",
            "MODELENGINE wolf_model"),
    RARITY("rarity", "Rarity bands",
            "<id> <qualityMin> <qualityMax> <weight> <hatchMultiplier>; repeat with ; or type none",
            "common 0 50 70 1.0;rare 50 100 30 1.5"),
    PROGRESSION("progression", "Progression",
            "<maxLevel>;<experienceFormula> or none",
            "50;100 * level"),
    SKILLS("skills", "Skills",
            "<provider:id>|<trigger>|<cooldown>|<chance>|<stamina>|<target>; repeat with ; or type none",
            "mythicmobs:Fireball|ACTIVE|30s|1.0|10|SELF"),
    BEHAVIOR("behavior", "Behavior and effects",
            "key=value; repeat with ; or type none",
            "glow=true;particle=flame"),
    RELEASE("release", "Release policy",
            "one mode token, or none",
            "INTERNAL_ONLY"),
    STATS_MANUAL("stats", "Manual stat list",
            "<id> <FLAT|RELATIVE|ADDITIVE_MULTIPLIER> <min> <max>; repeat with ; or type none",
            "ATTACK_DAMAGE FLAT 5 10;MAX_HEALTH FLAT 20 40"),
    STAT_SEARCH("stats.search", "Stat search",
            "part of a stat name or ID, or none to clear",
            "attack"),
    DEFINITION_ID("definition.id", "Definition ID",
            "lowercase letters, digits, and underscores",
            "arctic_wolf"),
    CLONE_ID("clone.definition.id", "Clone definition ID",
            "lowercase letters, digits, and underscores, different from the source",
            "arctic_wolf_v2"),
    LIST_SEARCH("search", "Definition search",
            "part of a definition ID, or none to clear",
            "wolf");

    private final String path;
    private final String field;
    private final String format;
    private final String example;

    StudioFieldPrompt(String path, String field, String format, String example) {
        this.path = path;
        this.field = field;
        this.format = format;
        this.example = example;
    }

    /** The chat-input path this prompt belongs to, matching the existing await keys. */
    String path() {
        return path;
    }

    String field() {
        return field;
    }

    /** Sends the four-line prompt block: what, format, example, how to abort. */
    void send(Player player) {
        Objects.requireNonNull(player, "player");
        player.sendMessage(Messages.line(MessageKey.STUDIO_PROMPT_FIELD, Messages.of("field", field)));
        player.sendMessage(Messages.line(MessageKey.STUDIO_PROMPT_FORMAT, Messages.of("format", format)));
        player.sendMessage(Messages.line(MessageKey.STUDIO_PROMPT_EXAMPLE, Messages.of("example", example)));
        player.sendMessage(Messages.line(MessageKey.STUDIO_PROMPT_CANCEL));
    }

    /**
     * Sends the prompt for a catalog stat whose modifier was already chosen by click, so the operator
     * only needs to type {@code min max}.
     */
    static void sendStatRange(Player player, String statDisplayName, StatModifierType modifier) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(modifier, "modifier");
        player.sendMessage(Messages.line(MessageKey.STUDIO_MODIFIER_PROMPT,
                Messages.of("stat", statDisplayName),
                Messages.of("modifier", StatModifierPresentation.label(modifier))));
        player.sendMessage(Messages.line(MessageKey.STUDIO_PROMPT_FORMAT, Messages.of("format", "<min> <max>")));
        player.sendMessage(Messages.line(MessageKey.STUDIO_PROMPT_EXAMPLE, Messages.of("example", "10 50")));
        player.sendMessage(Messages.line(MessageKey.STUDIO_MODIFIER_RANGE,
                Messages.of("detail", StatModifierPresentation.rangeHint(modifier))));
        player.sendMessage(Messages.line(MessageKey.STUDIO_PROMPT_CANCEL));
    }

    /** Every prompt path, used by the contract test to prove no await path is left silent. */
    static List<String> paths() {
        return java.util.Arrays.stream(values()).map(StudioFieldPrompt::path).toList();
    }

    /** Resolves the stat-specific await path {@code stats.<id>} to its shared prompt. */
    static boolean isStatValuePath(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).startsWith("stats.")
                && !path.equalsIgnoreCase(STAT_SEARCH.path);
    }
}
