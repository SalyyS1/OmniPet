package io.github.salyvn.omnipet.core.runtime;

@FunctionalInterface
public interface ActivationRendererResolver {
    PetRendererPort resolve(RendererSpawnRequest request);
}
