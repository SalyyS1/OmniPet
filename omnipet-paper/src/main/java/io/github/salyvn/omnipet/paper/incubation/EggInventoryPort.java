package io.github.salyvn.omnipet.paper.incubation;

import java.util.List;

import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public interface EggInventoryPort {
    List<ObservedEggStack> stacks();

    boolean handMatches(EggItemIdentity identity);

    boolean removeOne(ObservedEggStack expected);

    boolean addOne(ObservedEggStack expected);

    boolean restoreOne(EggItemIdentity identity);
}
