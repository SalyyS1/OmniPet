package io.github.salyvn.omnipet.core.studio;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** Provider-neutral release behavior with an extension seam for future renderers. */
public record ReleasePolicy(String mode, Map<String, Object> extensions) {
    public ReleasePolicy {
        mode = StudioStat.requireReference(mode, "release policy");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }
}
