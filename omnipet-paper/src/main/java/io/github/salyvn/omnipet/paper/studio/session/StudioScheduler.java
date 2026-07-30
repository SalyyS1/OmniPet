package io.github.salyvn.omnipet.paper.studio.session;

import java.time.Duration;

public interface StudioScheduler {
    ScheduledHandle schedule(Duration delay, Runnable task);

    interface ScheduledHandle {
        void cancel();
    }
}
