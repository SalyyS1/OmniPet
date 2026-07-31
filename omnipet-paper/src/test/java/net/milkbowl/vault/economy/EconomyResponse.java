package net.milkbowl.vault.economy;

public class EconomyResponse {
    public final String errorMessage;
    private final boolean success;

    public EconomyResponse(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public boolean transactionSuccess() {
        return success;
    }
}
