package io.github.salyvn.omnipet.core.economy;

public record EconomyOperationResult(Status status, String evidence) {
    public EconomyOperationResult {
        if (status == null) throw new IllegalArgumentException("economy result status is required");
        evidence = evidence == null ? "" : evidence;
        if (evidence.length() > 512) throw new IllegalArgumentException("economy evidence is too long");
    }

    public static EconomyOperationResult succeeded(String evidence) {
        return new EconomyOperationResult(Status.PROVEN_SUCCESS, evidence);
    }

    public static EconomyOperationResult failed(String evidence) {
        return new EconomyOperationResult(Status.PROVEN_FAILURE, evidence);
    }

    public static EconomyOperationResult unavailable(String evidence) {
        return new EconomyOperationResult(Status.UNAVAILABLE, evidence);
    }

    public static EconomyOperationResult unknown(String evidence) {
        return new EconomyOperationResult(Status.UNKNOWN_COMMIT, evidence);
    }

    public boolean provenSuccess() {
        return status == Status.PROVEN_SUCCESS;
    }

    public boolean ambiguous() {
        return status == Status.UNKNOWN_COMMIT;
    }

    public enum Status {
        PROVEN_SUCCESS,
        PROVEN_FAILURE,
        UNAVAILABLE,
        UNKNOWN_COMMIT
    }
}
