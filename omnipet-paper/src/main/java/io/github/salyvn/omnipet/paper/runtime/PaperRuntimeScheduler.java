package io.github.salyvn.omnipet.paper.runtime;

/** Main-thread scheduler seam; the Bukkit adapter is supplied by plugin bootstrap. */
public interface PaperRuntimeScheduler {
    boolean isMainThread();

    ScheduledTask scheduleRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    interface ScheduledTask {
        void cancel();

        boolean cancelled();
    }
}
