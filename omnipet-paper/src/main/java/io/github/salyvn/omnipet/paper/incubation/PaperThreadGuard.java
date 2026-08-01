package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;

final class PaperThreadGuard {
    private final BooleanSupplier primaryThread;

    PaperThreadGuard() { this(Bukkit::isPrimaryThread); }

    PaperThreadGuard(BooleanSupplier primaryThread) {
        this.primaryThread = Objects.requireNonNull(primaryThread, "primary thread probe");
    }

    void check() {
        if (!primaryThread.getAsBoolean()) {
            throw new IllegalStateException("Paper egg inventory access must run on the primary thread");
        }
    }
}
