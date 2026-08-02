package io.github.salyvn.omnipet.paper.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementMenuRenderer;

final class PetManagementMenuSupport {
    private PetManagementMenuSupport() {}

    static ProgressionMutationContext progressionContext(
            PetManagementViewModel view,
            ProgressionConfig progression,
            RegistrySnapshotRepository registry) {
        if (view == null) throw new IllegalStateException("management view is stale");
        ProgressionConfig effective = effectiveConfig(view, progression, registry);
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("evolution", (double) view.progression().evolution());
        Object raw = view.pet().rawComponents().get("hatching");
        if (raw instanceof Map<?, ?> hatching) {
            Object quality = hatching.get("qualityScore");
            if (quality instanceof Number number && Double.isFinite(number.doubleValue())) {
                values.put("quality", number.doubleValue());
            }
            Object rarity = hatching.get("rarityId");
            if (rarity instanceof String name) values.put("rarity", rarityValue(name));
        }
        return new ProgressionMutationContext(
                effective, petFormula(view, registry), values,
                effective.maxStamina(), System.currentTimeMillis());
    }

    private static ProgressionConfig effectiveConfig(
            PetManagementViewModel view,
            ProgressionConfig base,
            RegistrySnapshotRepository registry) {
        PetDefinition definition = registry.current().definitions().get(view.pet().definitionId());
        if (definition == null) return base;
        Object raw = definition.rawNode().get("progression");
        if (!(raw instanceof Map<?, ?> node) || !(node.get("maxLevel") instanceof Number number)) return base;
        int maximum = number.intValue();
        if (maximum < 1) return base;
        return new ProgressionConfig(
                Math.min(base.maxLevel(), maximum), base.maxStamina(), base.staminaRegenPerSecond(),
                base.defaultFormula(), base.formulaSamples(), base.overflowPolicy());
    }

    private static String petFormula(PetManagementViewModel view, RegistrySnapshotRepository registry) {
        PetDefinition definition = registry.current().definitions().get(view.pet().definitionId());
        if (definition == null) return null;
        Object raw = definition.rawNode().get("progression");
        if (!(raw instanceof Map<?, ?> node)) return null;
        Object formula = node.get("experienceFormula");
        return formula instanceof String value && !value.isBlank() ? value : null;
    }

    private static double rarityValue(String name) {
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "COMMON" -> 1;
            case "UNCOMMON" -> 2;
            case "RARE" -> 3;
            case "EPIC" -> 4;
            case "LEGENDARY", "MYTHIC" -> 5;
            default -> 1;
        };
    }

    static void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text("OmniPet: " + text, color));
    }

    static String detail(Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    static final class OutcomeRouter {
        private final PaperPetManagementController management;
        private final PetManagementMenuRenderer renderer;
        private final Consumer<Player> storageRefresh;
        private final ViewLifecycle lifecycle;
        private final BiConsumer<ViewLifecycle.Request, Runnable> dispatchMain;
        private final Predicate<Player> available;

        OutcomeRouter(
                PaperPetManagementController management,
                PetManagementMenuRenderer renderer,
                Consumer<Player> storageRefresh,
                ViewLifecycle lifecycle,
                BiConsumer<ViewLifecycle.Request, Runnable> dispatchMain,
                Predicate<Player> available) {
            this.management = management;
            this.renderer = renderer;
            this.storageRefresh = storageRefresh;
            this.lifecycle = lifecycle;
            this.dispatchMain = dispatchMain;
            this.available = available;
        }

        void handle(Player player, ViewLifecycle.Request request, PetManagementOutcome outcome) {
            if (outcome == null) {
                message(player, "Management action returned no result.", NamedTextColor.RED);
            } else if (outcome.view() != null) {
                openView(player, request, outcome);
            } else if (outcome.status() == PetManagementOutcome.Status.RELEASE_COMMITTED_OUTBOX_PENDING) {
                finishRelease(player, request, outcome);
            } else {
                handleStatus(player, request, outcome);
            }
        }

        private void openView(Player player, ViewLifecycle.Request request, PetManagementOutcome outcome) {
            if (!player.getUniqueId().equals(outcome.view().session().viewerId())) {
                message(player, "Management result viewer mismatch.", NamedTextColor.RED);
                return;
            }
            Inventory inventory = renderer.render(outcome.view());
            if (!(inventory.getHolder() instanceof PetManagementInventoryHolder holder)
                    || !lifecycle.replace(request, holder, outcome.view())) return;
            player.openInventory(inventory);
            if (outcome.status() == PetManagementOutcome.Status.PERSISTED
                    || outcome.status() == PetManagementOutcome.Status.PERSISTED_CONSUMPTION_PENDING) {
                storageRefresh.accept(player);
            }
            if (outcome.status() == PetManagementOutcome.Status.PERSISTED_CONSUMPTION_PENDING) {
                message(player, outcome.detail(), NamedTextColor.RED);
            }
        }

        private void handleStatus(Player player, ViewLifecycle.Request request, PetManagementOutcome outcome) {
            NamedTextColor color = outcome.status() == PetManagementOutcome.Status.BUSY
                    || outcome.status() == PetManagementOutcome.Status.STALE_SESSION
                    ? NamedTextColor.YELLOW : NamedTextColor.RED;
            String text = outcome.detail().isBlank()
                    ? outcome.status().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    : outcome.detail();
            message(player, text, color);
            if (outcome.status() == PetManagementOutcome.Status.STALE_SESSION
                    || outcome.status() == PetManagementOutcome.Status.PET_NOT_FOUND) {
                retireAndClose(player, request);
            }
        }

        private void finishRelease(Player player, ViewLifecycle.Request request, PetManagementOutcome outcome) {
            if (outcome.releasePreview() == null || outcome.transactionId() == null) {
                message(player, "Release result is missing recovery identity.", NamedTextColor.RED);
                return;
            }
            retireAndClose(player, request);
            storageRefresh.accept(player);
            message(player, "Pet released; delivering the frozen reward outbox.", NamedTextColor.GREEN);
            management.recoverRelease(
                    player.getUniqueId(), player.getUniqueId(),
                    outcome.releasePreview().petId(), outcome.transactionId())
                    .whenComplete((recovered, failure) -> dispatchMain.accept(null, () -> {
                        if (!available.test(player)) return;
                        if (failure != null) {
                            message(player, "Release reward recovery failed: " + detail(failure) + ".",
                                    NamedTextColor.RED);
                        } else if (recovered == null) {
                            message(player, "Release reward recovery returned no result.", NamedTextColor.RED);
                        } else {
                            message(player, recovered.detail(),
                                    recovered.status() == PetManagementOutcome.Status.OUTBOX_RECOVERED
                                            ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
                        }
                    }));
        }

        private void retireAndClose(Player player, ViewLifecycle.Request request) {
            ViewLifecycle.ActiveView retired = lifecycle.retire(request);
            if (retired == null) return;
            management.close(retired.holder().session());
            if (player.getOpenInventory().getTopInventory().getHolder() == retired.holder()) {
                player.closeInventory();
            }
        }
    }

    static final class ViewLifecycle {
        private final Map<UUID, ActiveView> views = new LinkedHashMap<>();
        private final Map<UUID, Request> requests = new LinkedHashMap<>();
        private boolean shutdown;

        synchronized Request beginOpen(UUID viewerId) {
            return begin(viewerId, null, false);
        }

        synchronized Request beginAction(PetManagementInventoryHolder holder) {
            return begin(holder.viewerId(), holder, true);
        }

        private Request begin(UUID viewerId, PetManagementInventoryHolder required, boolean requireCurrent) {
            if (shutdown || requests.containsKey(viewerId)) return null;
            ActiveView active = views.get(viewerId);
            if (requireCurrent && (active == null || active.holder() != required)) return null;
            Request request = new Request(viewerId, UUID.randomUUID(), active == null ? null : active.holder());
            requests.put(viewerId, request);
            return request;
        }

        synchronized boolean accepts(Request request) {
            if (request == null || requests.get(request.viewerId()) != request) return false;
            ActiveView active = views.get(request.viewerId());
            return active == null ? request.origin() == null : active.holder() == request.origin();
        }

        synchronized boolean replace(
                Request request,
                PetManagementInventoryHolder holder,
                PetManagementViewModel view) {
            if (!accepts(request) || !request.viewerId().equals(holder.viewerId())) return false;
            if (request.origin() != null
                    && request.origin().generationId().equals(holder.generationId())) return false;
            views.put(request.viewerId(), new ActiveView(holder, view));
            requests.remove(request.viewerId(), request);
            return true;
        }

        synchronized boolean current(PetManagementInventoryHolder holder) {
            ActiveView active = holder == null ? null : views.get(holder.viewerId());
            return active != null && active.holder() == holder
                    && active.holder().generationId().equals(holder.generationId());
        }

        synchronized PetManagementViewModel view(PetManagementInventoryHolder holder) {
            ActiveView active = current(holder) ? views.get(holder.viewerId()) : null;
            return active == null ? null : active.view();
        }

        synchronized ActiveView retire(PetManagementInventoryHolder holder, Inventory inventory) {
            if (!current(holder) || !holder.owns(inventory)) return null;
            requests.remove(holder.viewerId());
            return views.remove(holder.viewerId());
        }

        synchronized ActiveView retire(Request request) {
            if (!accepts(request)) return null;
            requests.remove(request.viewerId(), request);
            return views.remove(request.viewerId());
        }

        synchronized void complete(Request request) {
            if (request != null) requests.remove(request.viewerId(), request);
        }

        synchronized List<ActiveView> shutdown() {
            shutdown = true;
            requests.clear();
            List<ActiveView> active = new ArrayList<>(views.values());
            views.clear();
            return List.copyOf(active);
        }

        record Request(UUID viewerId, UUID requestId, PetManagementInventoryHolder origin) {}
        record ActiveView(PetManagementInventoryHolder holder, PetManagementViewModel view) {}
    }
}
