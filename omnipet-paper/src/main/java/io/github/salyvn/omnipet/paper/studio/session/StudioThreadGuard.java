package io.github.salyvn.omnipet.paper.studio.session;

@FunctionalInterface
public interface StudioThreadGuard {
    void assertMainThread();
}
