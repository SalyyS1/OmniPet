package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.runtime.InteractionIndex;
import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.PetActivationService;
import io.github.salyvn.omnipet.paper.render.PaperHeadRenderer;
import io.github.salyvn.omnipet.paper.render.PaperHeadRendererSettings;
import io.github.salyvn.omnipet.paper.render.PaperModelEngineRendererResolver;

public final class PaperRuntimeBootstrap {
    private PaperRuntimeBootstrap() {}

    public static PaperPetRuntimeCoordinator create(JavaPlugin plugin) {
        return create(plugin, PaperRuntimeSettings.defaults());
    }

    public static PaperPetRuntimeCoordinator create(JavaPlugin plugin, PaperRuntimeSettings settings) {
        return create(plugin, settings, PaperHeadRendererSettings.defaults());
    }

    /** Both renderers share {@code render}, so retuning movement cannot apply to only one of them. */
    public static PaperPetRuntimeCoordinator create(
            JavaPlugin plugin, PaperRuntimeSettings settings, PaperHeadRendererSettings render) {
        return create(plugin, settings, render, IdlePlaySettings.defaults());
    }

    /** Idle play and its vanity particles are wired here so a disabled feature installs a no-op sink. */
    public static PaperPetRuntimeCoordinator create(
            JavaPlugin plugin,
            PaperRuntimeSettings settings,
            PaperHeadRendererSettings render,
            IdlePlaySettings idlePlay) {
        Objects.requireNonNull(plugin, "runtime plugin");
        Objects.requireNonNull(settings, "runtime settings");
        Objects.requireNonNull(render, "renderer settings");
        Objects.requireNonNull(idlePlay, "idle-play settings");
        PaperHeadRenderer head = new PaperHeadRenderer(render);
        HeadFallbackRendererResolver renderers = new HeadFallbackRendererResolver(
                new PaperModelEngineRendererResolver(plugin, render),
                head,
                // Says why a pet authored for ModelEngine is rendering as a player head. Without this the
                // fallback was silent and the only way to learn the reason was to read the resolver.
                reason -> plugin.getLogger().warning("OmniPet renderer fallback — " + reason));
        // Retained rather than discarded: this index is already populated on spawn and purged on
        // remove, so exposing it is all that stood between a rendered pet and a right-click.
        InteractionIndex interactions = new InteractionIndex();
        PetVanityParticleSink particles = idlePlay.enabled()
                ? new BukkitPetVanityParticleSink(idlePlay)
                : PetVanityParticleSink.NONE;
        return new PaperPetRuntimeCoordinator(
                new BukkitPaperRuntimeScheduler(plugin),
                settings,
                new PetActivationService(interactions),
                new MovementController(),
                renderers,
                new BukkitPaperRuntimeOwnerPoseSource(),
                System::nanoTime,
                failure -> plugin.getLogger().warning(format(failure)),
                interactions,
                idlePlay,
                particles);
    }

    private static String format(PaperRuntimeFailure failure) {
        String owner = failure.ownerId() == null ? "global" : failure.ownerId().toString();
        String pet = failure.petInstanceId() == null ? "" : " pet=" + failure.petInstanceId();
        return "OmniPet runtime " + failure.stage().name().toLowerCase()
                + " failure owner=" + owner + pet + ": " + failure.detail();
    }
}
