package io.github.salyvn.omnipet.paper.economy;

import java.util.concurrent.Callable;

@FunctionalInterface
interface ProviderCallExecutor {
    Object call(Callable<?> operation) throws Exception;
}
