package io.github.salyvn.omnipet.paper.render;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.runtime.ActivationRendererResolver;
import io.github.salyvn.omnipet.core.runtime.PetRendererPort;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;

public final class PaperModelEngineRendererResolver implements ActivationRendererResolver {
    private final JavaPlugin plugin;
    private final PaperHeadRendererSettings settings;
    private Plugin provider;
    private PaperModelEngineRenderer renderer;

    public PaperModelEngineRendererResolver(JavaPlugin plugin) {
        this(plugin, PaperHeadRendererSettings.defaults());
    }

    public PaperModelEngineRendererResolver(JavaPlugin plugin, PaperHeadRendererSettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "renderer settings");
    }

    @Override
    public synchronized PetRendererPort resolve(RendererSpawnRequest request) {
        if (!request.appearance().provider().equals("MODELENGINE")) {
            throw new IllegalArgumentException("no optional renderer supports " + request.appearance().provider());
        }
        Plugin current = plugin.getServer().getPluginManager().getPlugin("ModelEngine");
        if (current == null || !current.isEnabled()) throw new IllegalStateException("ModelEngine is not enabled");
        if (current != provider || renderer == null || !renderer.health().available()) {
            provider = current;
            renderer = new PaperModelEngineRenderer(current.getClass().getClassLoader(), settings);
            // Said once per provider instance, not per pet: an operator who expected animated pets needs
            // to know clips are off, but a per-spawn line would flood the log.
            String animationFailure = renderer.animationUnavailableDetail();
            if (animationFailure != null) {
                plugin.getLogger().warning("OmniPet: ModelEngine pets will render without animation - "
                        + animationFailure);
            }
        }
        return renderer;
    }
}
