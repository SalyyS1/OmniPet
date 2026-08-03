package io.github.salyvn.omnipet.paper.feedback;

import java.util.Locale;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/**
 * Turns a configured sound name into a {@link Sound}, or {@code null} when this server has none.
 *
 * <p>A seam for one reason: the production implementation must go through {@link Registry#SOUNDS},
 * and {@code Registry} cannot be class-loaded without a running server, so a unit test that resolved
 * a sound would fail with {@code No RegistryAccess implementation found}.
 *
 * <p>{@code Sound.valueOf} would be testable and is what this code originally used. It is wrong:
 * {@code Sound} is an {@code enum} on Paper 1.21 and 1.21.1 but an {@code interface} from 1.21.11
 * onward, so the call compiles to a {@code Methodref} against the enum and throws
 * {@code IncompatibleClassChangeError} at class-load time on the newer line — the plugin fails to
 * enable at all. Testability is not worth a hard startup failure, so the seam exists instead.
 */
@FunctionalInterface
public interface SoundResolver {
    Sound resolve(String name);

    /**
     * Resolves through the Paper registry, whose shape is identical on both Paper lines.
     *
     * <p>Accepts a namespaced key ({@code minecraft:entity.experience_orb.pickup}) directly, and the
     * constant form ({@code ENTITY_EXPERIENCE_ORB_PICKUP}) by scanning the registry and comparing
     * normalised keys.
     *
     * <p>The scan is not laziness. A constant name cannot be converted back into a key by rule:
     * {@code ENTITY_EXPERIENCE_ORB_PICKUP} is {@code entity.experience_orb.pickup} — one underscore
     * survives — while {@code BLOCK_CHEST_LOCKED} is {@code block.chest.locked}, where none does.
     * Bukkit generates the constant name *from* the key by replacing dots with underscores and upper-
     * casing, so comparing in that direction is exact where guessing the reverse is not. It runs once
     * per configured cue at startup, four times total, over a few thousand entries.
     */
    static SoundResolver registry() {
        return name -> {
            if (name == null || name.isBlank()) return null;
            String trimmed = name.trim();
            // A key the operator wrote out in full, e.g. minecraft:block.chest.locked.
            NamespacedKey key = NamespacedKey.fromString(trimmed.toLowerCase(Locale.ROOT));
            if (key != null) {
                Sound direct = Registry.SOUNDS.get(key);
                if (direct != null) return direct;
            }
            // Otherwise treat it as the constant form and match against generated constant names.
            String wanted = trimmed.toUpperCase(Locale.ROOT);
            for (Sound candidate : Registry.SOUNDS) {
                if (constantName(candidate).equals(wanted)) return candidate;
            }
            return null;
        };
    }

    /**
     * The constant name Bukkit generates for a sound: {@code block.chest.locked} to
     * {@code BLOCK_CHEST_LOCKED}.
     *
     * <p>The parameter is typed {@link Keyed}, not {@code Sound}, on purpose. Calling
     * {@code Sound.getKey()} compiles to an {@code invokevirtual} against the 1.21 enum and would fail
     * at class load on 1.21.11 with the same {@code IncompatibleClassChangeError} this class exists to
     * avoid. {@code Keyed} is an interface on both lines, so the call site emits
     * {@code invokeinterface} either way.
     */
    private static String constantName(Keyed sound) {
        return sound.getKey().getKey().replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
