package io.github.salyvn.omnipet.paper.render;

final class PaperHeadMovementPolicy {
    private PaperHeadMovementPolicy() {}

    static Decision decide(
            boolean entitiesValid,
            boolean sameWorld,
            boolean currentChunkLoaded,
            boolean targetChunkLoaded,
            double distanceSquared,
            double safetyDistance) {
        if (!targetChunkLoaded) return Decision.REJECT_UNLOADED_TARGET;
        if (!entitiesValid) return Decision.HARD_INVALID_ENTITY;
        if (!sameWorld) return Decision.HARD_WORLD_MISMATCH;
        if (!currentChunkLoaded) return Decision.HARD_UNLOADED_RECOVERY;
        if (distanceSquared > safetyDistance * safetyDistance) return Decision.HARD_SAFETY_DISTANCE;
        return Decision.SMOOTH;
    }

    enum Decision {
        SMOOTH,
        HARD_INVALID_ENTITY,
        HARD_WORLD_MISMATCH,
        HARD_UNLOADED_RECOVERY,
        HARD_SAFETY_DISTANCE,
        REJECT_UNLOADED_TARGET;

        boolean requiresRespawn() {
            return this == HARD_INVALID_ENTITY;
        }

        boolean hardTeleport() {
            return this != SMOOTH && this != REJECT_UNLOADED_TARGET && !requiresRespawn();
        }
    }
}
