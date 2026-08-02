package io.github.salyvn.omnipet.paper.runtime;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface PaperRuntimeOwnerPoseSource {
    Optional<PaperRuntimeOwnerPose> find(UUID ownerId);
}
