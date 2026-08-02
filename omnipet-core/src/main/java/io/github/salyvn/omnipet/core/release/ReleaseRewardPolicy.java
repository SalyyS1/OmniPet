package io.github.salyvn.omnipet.core.release;

import io.github.salyvn.omnipet.core.domain.PetInstance;

@FunctionalInterface
public interface ReleaseRewardPolicy {
    ReleaseRewardBundle calculate(PetInstance pet);
}
