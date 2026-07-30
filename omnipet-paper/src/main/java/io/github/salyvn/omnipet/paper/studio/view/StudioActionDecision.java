package io.github.salyvn.omnipet.paper.studio.view;

public record StudioActionDecision(boolean allowed, String reason) {
    public StudioActionDecision {
        if (allowed && reason != null) throw new IllegalArgumentException("allowed action cannot have a denial reason");
        if (!allowed && (reason == null || reason.isBlank())) throw new IllegalArgumentException("denial reason is required");
    }

    public static StudioActionDecision allow() {
        return new StudioActionDecision(true, null);
    }

    public static StudioActionDecision deny(String reason) {
        return new StudioActionDecision(false, reason);
    }

    /** Studio inventories are always read-only; allowed means controller dispatch only. */
    public boolean cancelInventoryMutation() {
        return true;
    }
}
