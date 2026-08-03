package io.github.salyvn.omnipet.paper.feedback;

import java.util.Objects;

import org.bukkit.Sound;

/** One playable sound with its volume and pitch, resolved against this server's registry. */
public record ResolvedSound(Sound sound, float volume, float pitch) {
    public ResolvedSound {
        Objects.requireNonNull(sound, "sound");
    }
}
