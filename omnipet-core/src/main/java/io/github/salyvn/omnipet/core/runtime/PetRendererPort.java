package io.github.salyvn.omnipet.core.runtime;

/** Vendor-neutral renderer lifecycle. Paper implementations enforce main-thread access. */
public interface PetRendererPort {
    RendererHealth health();

    RendererHandle spawn(RendererSpawnRequest request);

    void update(RendererHandle handle, RuntimeTransform transform);

    void updateAppearance(RendererHandle handle, RendererAppearance appearance);

    void remove(RendererHandle handle);
}
