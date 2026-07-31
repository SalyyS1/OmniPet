package io.github.salyvn.omnipet.core.economy;

/** Vendor-neutral outcome for the durable post-purchase entitlement step. */
public record ExternalEntitlementResult(boolean succeeded, String detail) {
    public ExternalEntitlementResult {
        detail = detail == null ? "" : detail;
        if (detail.length() > 512) throw new IllegalArgumentException("entitlement detail is too long");
    }

    public static ExternalEntitlementResult completed(String detail) {
        return new ExternalEntitlementResult(true, detail);
    }

    public static ExternalEntitlementResult pending(String detail) {
        return new ExternalEntitlementResult(false, detail);
    }
}
