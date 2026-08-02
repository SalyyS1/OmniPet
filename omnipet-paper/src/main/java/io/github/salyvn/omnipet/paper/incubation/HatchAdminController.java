package io.github.salyvn.omnipet.paper.incubation;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.incubation.HatchResult;
import io.github.salyvn.omnipet.core.incubation.RepositoryHatchService;
import io.github.salyvn.omnipet.paper.command.HatchAdminCommandTarget;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;

/** Serializes offline hatch administration with every other accepted player task. */
public final class HatchAdminController implements HatchAdminCommandTarget {
    private final HatchOperations hatches;
    private final TaskSubmitter tasks;
    private final Consumer<Runnable> mainDispatcher;
    private final Consumer<String> warningLogger;

    public HatchAdminController(
            JavaPlugin plugin,
            RepositoryHatchService hatches,
            PerPlayerTaskQueue tasks) {
        this(
                new RepositoryHatchOperations(hatches),
                taskSubmitter(tasks),
                mainDispatcher(plugin),
                warningLogger(plugin));
    }

    HatchAdminController(
            HatchOperations hatches,
            TaskSubmitter tasks,
            Consumer<Runnable> mainDispatcher,
            Consumer<String> warningLogger) {
        this.hatches = Objects.requireNonNull(hatches, "hatch operations");
        this.tasks = Objects.requireNonNull(tasks, "player task submitter");
        this.mainDispatcher = Objects.requireNonNull(mainDispatcher, "main-thread dispatcher");
        this.warningLogger = Objects.requireNonNull(warningLogger, "warning logger");
    }

    @Override
    public void inspect(CommandSender sender, UUID playerId) {
        submit(sender, playerId, "inspect", () -> formatSnapshot(playerId, hatches.snapshot(playerId)));
    }

    @Override
    public void reduce(
            CommandSender sender,
            UUID playerId,
            UUID incubationId,
            long millis,
            UUID actionId) {
        mutate(sender, playerId, incubationId, "reduce",
                revision -> hatches.reduce(playerId, revision, incubationId, millis, actionId));
    }

    @Override
    public void setRemaining(
            CommandSender sender,
            UUID playerId,
            UUID incubationId,
            long millis,
            UUID actionId) {
        mutate(sender, playerId, incubationId, "set",
                revision -> hatches.setRemaining(playerId, revision, incubationId, millis, actionId));
    }

    @Override
    public void complete(CommandSender sender, UUID playerId, UUID incubationId, UUID actionId) {
        mutate(sender, playerId, incubationId, "complete",
                revision -> hatches.complete(playerId, revision, incubationId, actionId));
    }

    @Override
    public void cancel(CommandSender sender, UUID playerId, UUID incubationId, UUID actionId) {
        mutate(sender, playerId, incubationId, "cancel",
                revision -> hatches.cancel(playerId, revision, incubationId, actionId));
    }

    private void mutate(
            CommandSender sender,
            UUID playerId,
            UUID incubationId,
            String operation,
            CheckedMutation mutation) {
        Objects.requireNonNull(incubationId, "incubation id");
        submit(sender, playerId, operation, () -> {
            PlayerState snapshot = hatches.snapshot(playerId);
            IncubationState current = snapshot.incubation();
            if (current == null) {
                return "OmniPet: hatch " + operation + " rejected player=" + playerId
                        + " incubation=none revision=" + snapshot.revision() + ".";
            }
            if (!current.id().equals(incubationId)) {
                return "OmniPet: hatch " + operation + " rejected player=" + playerId
                        + " requested=" + incubationId + " current=" + current.id()
                        + " revision=" + snapshot.revision() + ".";
            }
            return formatMutation(operation, playerId, mutation.run(snapshot.revision()));
        });
    }

    private void submit(CommandSender sender, UUID playerId, String operation, CheckedMessage task) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(playerId, "player id");
        Runnable queued = () -> {
            try {
                sendMain(sender, task.run());
            } catch (IOException | RuntimeException failure) {
                warningLogger.accept("Hatch admin " + operation + " failed for " + playerId + ": "
                        + detail(failure));
                sendMain(sender, "OmniPet: hatch " + operation + " failed for player=" + playerId
                        + " - " + detail(failure) + ".");
            }
        };
        try {
            if (!tasks.submit(playerId, queued)) {
                sendMain(sender, "OmniPet: hatch " + operation + " could not be queued; service is shutting down.");
            }
        } catch (RuntimeException failure) {
            warningLogger.accept("Hatch admin " + operation + " could not be queued for " + playerId + ": "
                    + detail(failure));
            sendMain(sender, "OmniPet: hatch " + operation + " could not be queued.");
        }
    }

    private void sendMain(CommandSender sender, String message) {
        try {
            mainDispatcher.accept(() -> sender.sendMessage(message));
        } catch (RuntimeException failure) {
            warningLogger.accept("Hatch admin response could not reach the main thread: " + detail(failure));
        }
    }

    static String formatSnapshot(UUID playerId, PlayerState snapshot) {
        IncubationState incubation = snapshot.incubation();
        if (incubation == null) {
            return "OmniPet: hatch player=" + playerId + " revision=" + snapshot.revision()
                    + " incubation=none.";
        }
        return "OmniPet: hatch player=" + playerId + " revision=" + snapshot.revision()
                + " incubation=" + incubation.id() + " status=" + incubation.status()
                + " remaining=" + incubation.remainingActiveMillis() + "ms.";
    }

    static String formatMutation(String operation, UUID playerId, HatchResult result) {
        IncubationState incubation = result.incubation();
        String incubationDetail = incubation == null
                ? "incubation=none"
                : "incubation=" + incubation.id() + " status=" + incubation.status()
                        + " remaining=" + incubation.remainingActiveMillis() + "ms";
        return "OmniPet: hatch " + operation + " result="
                + result.status().name().toLowerCase(Locale.ROOT) + " player=" + playerId
                + " revision=" + result.state().revision() + " " + incubationDetail + ".";
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static TaskSubmitter taskSubmitter(PerPlayerTaskQueue tasks) {
        Objects.requireNonNull(tasks, "player task queue");
        return tasks::submit;
    }

    private static Consumer<Runnable> mainDispatcher(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private static Consumer<String> warningLogger(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return plugin.getLogger()::warning;
    }

    interface HatchOperations {
        PlayerState snapshot(UUID playerId) throws IOException;

        HatchResult reduce(UUID playerId, long revision, UUID incubationId, long millis, UUID actionId)
                throws IOException;

        HatchResult setRemaining(UUID playerId, long revision, UUID incubationId, long millis, UUID actionId)
                throws IOException;

        HatchResult complete(UUID playerId, long revision, UUID incubationId, UUID actionId) throws IOException;

        HatchResult cancel(UUID playerId, long revision, UUID incubationId, UUID actionId) throws IOException;
    }

    @FunctionalInterface
    interface TaskSubmitter {
        boolean submit(UUID playerId, Runnable task);
    }

    @FunctionalInterface
    private interface CheckedMessage {
        String run() throws IOException;
    }

    @FunctionalInterface
    private interface CheckedMutation {
        HatchResult run(long revision) throws IOException;
    }

    private record RepositoryHatchOperations(RepositoryHatchService service) implements HatchOperations {
        private RepositoryHatchOperations {
            Objects.requireNonNull(service, "hatch service");
        }

        @Override
        public PlayerState snapshot(UUID playerId) throws IOException {
            return service.snapshot(playerId);
        }

        @Override
        public HatchResult reduce(
                UUID playerId, long revision, UUID incubationId, long millis, UUID actionId) throws IOException {
            return service.reduce(playerId, revision, incubationId, millis, actionId);
        }

        @Override
        public HatchResult setRemaining(
                UUID playerId, long revision, UUID incubationId, long millis, UUID actionId) throws IOException {
            return service.setRemaining(playerId, revision, incubationId, millis, actionId);
        }

        @Override
        public HatchResult complete(UUID playerId, long revision, UUID incubationId, UUID actionId)
                throws IOException {
            return service.complete(playerId, revision, incubationId, actionId);
        }

        @Override
        public HatchResult cancel(UUID playerId, long revision, UUID incubationId, UUID actionId)
                throws IOException {
            return service.cancel(playerId, revision, incubationId, actionId);
        }
    }
}
