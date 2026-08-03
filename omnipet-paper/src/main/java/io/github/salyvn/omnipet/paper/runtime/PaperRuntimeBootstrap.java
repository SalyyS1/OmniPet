package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.runtime.InteractionIndex;
import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.PetActivationService;
import io.github.salyvn.omnipet.paper.render.PaperHeadRenderer;
import io.github.salyvn.omnipet.paper.render.PaperModelEngineRendererResolver;

public final class PaperRuntimeBootstrap {
    private PaperRuntimeBootstrap() {}

    public static PaperPetRuntimeCoordinator create(JavaPlugin plugin) {
        return create(plugin, PaperRuntimeSettings.defaults());
    }

    public static PaperPetRuntimeCoordinator create(JavaPlugin plugin, PaperRuntimeSettings settings) {
        Objects.requireNonNull(plugin, "runtime plugin");
        Objects.requireNonNull(settings, "runtime settings");
        PaperHeadRenderer head = new PaperHeadRenderer();
        HeadFallbackRendererResolver renderers = new HeadFallbackRendererResolver(
                new PaperModelEngineRendererResolver(plugin),
                head);
        // Retained rather than discarded: this index is already populated on spawn and purged on
        // remove, so exposing it is all that stood between a rendered pet and a right-click.
        InteractionIndex interactions = new InteractionIndex();
        return new PaperPetRuntimeCoordinator(
                new BukkitPaperRuntimeScheduler(plugin),
                settings,
                new PetActivationService(interactions),
                new MovementController(),
                renderers,
                new BukkitPaperRuntimeOwnerPoseSource(),
                System::nanoTime,
                failure -> plugin.getLogger().warning(format(failure)),
                interactions);
    }

    private static String format(PaperRuntimeFailure failure) {
        String owner = failure.ownerId() == null ? "global" : failure.ownerId().toString();
        String pet = failure.petInstanceId() == null ? "" : " pet=" + failure.petInstanceId();
        return "OmniPet runtime " + failure.stage().name().toLowerCase()
                + " failure owner=" + owner + pet + ": " + failure.detail();
    }
}
