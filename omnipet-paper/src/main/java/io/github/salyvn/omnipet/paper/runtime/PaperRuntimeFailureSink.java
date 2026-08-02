package io.github.salyvn.omnipet.paper.runtime;

@FunctionalInterface
public interface PaperRuntimeFailureSink {
    PaperRuntimeFailureSink IGNORE = failure -> {};

    void accept(PaperRuntimeFailure failure);
}
