package io.github.salyvn.omnipet.core.catalog;

/** Provider state exposed to Studio without linking the provider implementation. */
public enum CatalogHealth {
    AVAILABLE,
    UNAVAILABLE,
    DISABLED,
    INCOMPATIBLE
}
