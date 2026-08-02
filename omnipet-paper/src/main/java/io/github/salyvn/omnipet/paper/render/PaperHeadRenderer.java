package io.github.salyvn.omnipet.paper.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.runtime.PetRendererPort;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RendererHealth;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

/** Built-in Paper renderer using an invisible carrier, head display, and Interaction. */
public final class PaperHeadRenderer implements PetRendererPort {
    private final PaperHeadRendererBackend backend;
    private final PaperHeadRendererSettings settings;
    private final Map<UUID, PaperHeadRendererHandle> handles = new LinkedHashMap<>();

    public PaperHeadRenderer() {
        this(PaperHeadRendererSettings.defaults());
    }

    public PaperHeadRenderer(PaperHeadRendererSettings settings) {
        this(new BukkitPaperHeadRendererBackend(), settings);
    }

    PaperHeadRenderer(PaperHeadRendererBackend backend, PaperHeadRendererSettings settings) {
        this.backend = Objects.requireNonNull(backend, "HEAD renderer backend");
        this.settings = Objects.requireNonNull(settings, "HEAD renderer settings");
    }

    @Override
    public RendererHealth health() {
        return new RendererHealth(RendererHealth.Status.AVAILABLE, "built-in Paper HEAD renderer");
    }

    @Override
    public RendererHandle spawn(RendererSpawnRequest request) {
        assertMainThread();
        Objects.requireNonNull(request, "renderer spawn request");
        PaperHeadRendererHandle existing = handles.get(request.petInstanceId());
        if (existing != null && !existing.removed()) {
            if (!existing.ownerId().equals(request.ownerId())
                    || existing.rendererGeneration() != request.rendererGeneration()) {
                throw new IllegalStateException("HEAD renderer instance identity changed");
            }
            return existing;
        }

        PaperHeadRendererBackend.WorldRef world = requireOwnerWorld(request.ownerId());
        if (!backend.targetChunkLoaded(world, request.transform())) {
            throw new IllegalStateException("HEAD renderer target chunk is not loaded");
        }

        PaperHeadRendererBackend.EntityRef carrier = null;
        PaperHeadRendererBackend.EntityRef visual = null;
        PaperHeadRendererBackend.EntityRef interaction = null;
        try {
            carrier = backend.spawnCarrier(world, request.transform());
            visual = backend.spawnVisual(world, request.transform());
            interaction = backend.spawnInteraction(world, request.transform());
            backend.attach(carrier, visual);
            backend.attach(carrier, interaction);
            backend.updateAppearance(visual, request.appearance());
            backend.updateScale(visual, interaction, request.transform(), settings);
            PaperHeadRendererHandle handle = new PaperHeadRendererHandle(
                    request.ownerId(), request.petInstanceId(), request.rendererGeneration(),
                    carrier, visual, interaction, request.appearance(), request.transform());
            handles.put(request.petInstanceId(), handle);
            return handle;
        } catch (RuntimeException | LinkageError failure) {
            cleanupPartial(interaction, visual, carrier, failure);
            throw failure;
        }
    }

    @Override
    public void update(RendererHandle rawHandle, RuntimeTransform transform) {
        assertMainThread();
        PaperHeadRendererHandle handle = requireHandle(rawHandle);
        Objects.requireNonNull(transform, "renderer transform");
        PaperHeadRendererBackend.WorldRef targetWorld = requireOwnerWorld(handle.ownerId());
        boolean valid = backend.valid(handle.carrier())
                && backend.valid(handle.visual())
                && backend.valid(handle.interaction());
        boolean sameWorld = targetWorld.id().equals(backend.worldId(handle.carrier()));
        PaperHeadMovementPolicy.Decision decision = PaperHeadMovementPolicy.decide(
                valid,
                sameWorld,
                valid && backend.currentChunkLoaded(handle.carrier()),
                backend.targetChunkLoaded(targetWorld, transform),
                valid && sameWorld ? backend.distanceSquared(handle.carrier(), transform) : Double.POSITIVE_INFINITY,
                settings.safetyDistance());
        if (decision == PaperHeadMovementPolicy.Decision.REJECT_UNLOADED_TARGET) {
            throw new IllegalStateException("HEAD renderer target chunk is not loaded");
        }
        if (decision.requiresRespawn()) {
            retireInvalid(handle);
            throw new IllegalStateException("HEAD renderer entities became invalid and require respawn");
        }
        if (decision.hardTeleport()) {
            backend.hardTeleport(handle.carrier(), handle.visual(), handle.interaction(), targetWorld, transform);
        } else {
            backend.smoothMove(handle.carrier(), transform, settings);
        }
        backend.updateScale(handle.visual(), handle.interaction(), transform, settings);
        handle.transform(transform);
    }

    @Override
    public void updateAppearance(RendererHandle rawHandle, RendererAppearance appearance) {
        assertMainThread();
        PaperHeadRendererHandle handle = requireHandle(rawHandle);
        Objects.requireNonNull(appearance, "renderer appearance");
        backend.updateAppearance(handle.visual(), appearance);
        backend.updateScale(handle.visual(), handle.interaction(), handle.transform(), settings);
        handle.appearance(appearance);
    }

    @Override
    public void remove(RendererHandle rawHandle) {
        assertMainThread();
        if (!(rawHandle instanceof PaperHeadRendererHandle handle)) {
            throw new IllegalArgumentException("renderer handle was not created by PaperHeadRenderer");
        }
        if (handle.removed()) return;
        Throwable failure = null;
        failure = removeOne(handle.interaction(), failure);
        failure = removeOne(handle.visual(), failure);
        failure = removeOne(handle.carrier(), failure);
        handle.markRemoved();
        handles.remove(handle.petInstanceId(), handle);
        rethrow(failure);
    }

    private void retireInvalid(PaperHeadRendererHandle handle) {
        Throwable failure = null;
        failure = removeOne(handle.interaction(), failure);
        failure = removeOne(handle.visual(), failure);
        failure = removeOne(handle.carrier(), failure);
        handle.markRemoved();
        handles.remove(handle.petInstanceId(), handle);
        rethrow(failure);
    }

    private PaperHeadRendererHandle requireHandle(RendererHandle rawHandle) {
        if (!(rawHandle instanceof PaperHeadRendererHandle handle)
                || handle.removed()
                || handles.get(handle.petInstanceId()) != handle) {
            throw new IllegalArgumentException("HEAD renderer handle is invalid or removed");
        }
        return handle;
    }

    private PaperHeadRendererBackend.WorldRef requireOwnerWorld(UUID ownerId) {
        PaperHeadRendererBackend.WorldRef world = backend.ownerWorld(ownerId);
        if (world == null) throw new IllegalStateException("HEAD renderer owner is absent or has no valid world");
        return world;
    }

    private void assertMainThread() {
        if (!backend.isMainThread()) throw new IllegalStateException("HEAD renderer must run on the Paper main thread");
    }

    private void cleanupPartial(
            PaperHeadRendererBackend.EntityRef interaction,
            PaperHeadRendererBackend.EntityRef visual,
            PaperHeadRendererBackend.EntityRef carrier,
            Throwable failure) {
        Throwable cleanup = removeOne(interaction, null);
        cleanup = removeOne(visual, cleanup);
        cleanup = removeOne(carrier, cleanup);
        if (cleanup != null) failure.addSuppressed(cleanup);
    }

    private Throwable removeOne(PaperHeadRendererBackend.EntityRef entity, Throwable prior) {
        if (entity == null) return prior;
        try {
            backend.remove(entity);
            return prior;
        } catch (RuntimeException | LinkageError failure) {
            if (prior == null) return failure;
            prior.addSuppressed(failure);
            return prior;
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }
}
