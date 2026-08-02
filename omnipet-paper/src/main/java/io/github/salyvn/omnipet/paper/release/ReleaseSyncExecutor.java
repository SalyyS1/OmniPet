package io.github.salyvn.omnipet.paper.release;

import java.util.concurrent.Callable;

@FunctionalInterface
interface ReleaseSyncExecutor {
    <T> T call(Callable<T> operation) throws Exception;
}
