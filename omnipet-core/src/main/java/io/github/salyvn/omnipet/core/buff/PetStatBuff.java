package io.github.salyvn.omnipet.core.buff;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PetStatBuff(UUID petInstanceId, String statId, String modifierType, double value) {
    public PetStatBuff {
        petInstanceId = Objects.requireNonNull(petInstanceId, "buff pet instance ID");
        if (statId == null || statId.isBlank() || statId.length() > 128) {
            throw new IllegalArgumentException("buff stat ID is invalid");
        }
        statId = statId.trim();
        modifierType = modifierType == null || modifierType.isBlank()
                ? "FLAT"
                : modifierType.trim().toUpperCase(Locale.ROOT);
        if (!modifierType.equals("FLAT")
                && !modifierType.equals("RELATIVE")
                && !modifierType.equals("ADDITIVE_MULTIPLIER")) {
            throw new IllegalArgumentException("unsupported MythicLib modifier type: " + modifierType);
        }
        if (!Double.isFinite(value)) throw new IllegalArgumentException("buff value must be finite");
    }
}
