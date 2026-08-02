package io.github.salyvn.omnipet.paper.management;

public interface PetManagementMainThread {
    boolean isMainThread();

    void execute(Runnable task);
}
