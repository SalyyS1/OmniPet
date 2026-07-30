package io.github.salyvn.omnipet.paper.studio.input;

@FunctionalInterface
public interface StudioMainThreadDispatcher {
    void execute(Runnable task);
}
