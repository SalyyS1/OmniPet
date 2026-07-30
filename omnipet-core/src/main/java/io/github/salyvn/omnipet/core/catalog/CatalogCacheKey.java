package io.github.salyvn.omnipet.core.catalog;

/** Invalidates catalog data when either the registry or provider installation changes. */
public record CatalogCacheKey(long registryGeneration, String providerFingerprint) {
    public CatalogCacheKey {
        if (registryGeneration < 0) throw new IllegalArgumentException("registry generation cannot be negative");
        if (providerFingerprint == null || providerFingerprint.isBlank()) {
            throw new IllegalArgumentException("provider fingerprint is required");
        }
    }
}
