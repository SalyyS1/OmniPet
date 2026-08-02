package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaperHeadMovementPolicyTest {
    @Test
    void routineMovementStaysSmooth() {
        assertEquals(PaperHeadMovementPolicy.Decision.SMOOTH,
                PaperHeadMovementPolicy.decide(true, true, true, true, 4, 8));
    }

    @Test
    void hardTeleportReasonsAreExplicit() {
        assertEquals(PaperHeadMovementPolicy.Decision.HARD_INVALID_ENTITY,
                PaperHeadMovementPolicy.decide(false, true, true, true, 0, 8));
        assertEquals(PaperHeadMovementPolicy.Decision.HARD_WORLD_MISMATCH,
                PaperHeadMovementPolicy.decide(true, false, true, true, 0, 8));
        assertEquals(PaperHeadMovementPolicy.Decision.HARD_UNLOADED_RECOVERY,
                PaperHeadMovementPolicy.decide(true, true, false, true, 0, 8));
        assertEquals(PaperHeadMovementPolicy.Decision.HARD_SAFETY_DISTANCE,
                PaperHeadMovementPolicy.decide(true, true, true, true, 65, 8));
        assertEquals(PaperHeadMovementPolicy.Decision.REJECT_UNLOADED_TARGET,
                PaperHeadMovementPolicy.decide(true, true, true, false, 0, 8));
        assertEquals(true, PaperHeadMovementPolicy.Decision.HARD_INVALID_ENTITY.requiresRespawn());
        assertEquals(false, PaperHeadMovementPolicy.Decision.HARD_INVALID_ENTITY.hardTeleport());
    }
}
