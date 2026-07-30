package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.Objects;

public record StudioAction(StudioActionType type, String value) {
    public StudioAction {
        Objects.requireNonNull(type, "type");
        value = value == null ? "" : value;
    }
}
