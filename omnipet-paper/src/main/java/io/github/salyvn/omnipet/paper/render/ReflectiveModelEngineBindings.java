package io.github.salyvn.omnipet.paper.render;

import java.lang.reflect.Method;
import java.util.Objects;

import org.bukkit.entity.Entity;

final class ReflectiveModelEngineBindings {
    private final Method getBlueprint;
    private final Method createModeledEntity;
    private final Method createActiveModel;
    private final Method addModel;
    private final Method removeModel;
    private final Method setBaseEntityVisible;
    private final Method setScale;
    private final Method destroyModeledEntity;
    private final Method destroyActiveModel;

    ReflectiveModelEngineBindings(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI", true, loader);
        Class<?> modeled = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity", true, loader);
        Class<?> active = Class.forName("com.ticxo.modelengine.api.model.ActiveModel", true, loader);
        getBlueprint = api.getMethod("getBlueprint", String.class);
        createModeledEntity = api.getMethod("createModeledEntity", Entity.class);
        createActiveModel = api.getMethod("createActiveModel", String.class);
        addModel = modeled.getMethod("addModel", active, boolean.class);
        removeModel = modeled.getMethod("removeModel", String.class);
        setBaseEntityVisible = modeled.getMethod("setBaseEntityVisible", boolean.class);
        setScale = active.getMethod("setScale", double.class);
        destroyModeledEntity = modeled.getMethod("destroy");
        destroyActiveModel = active.getMethod("destroy");
    }

    boolean hasBlueprint(String assetId) throws ReflectiveOperationException {
        return getBlueprint.invoke(null, assetId) != null;
    }

    Object createModeled(Entity base) throws ReflectiveOperationException {
        Object result = createModeledEntity.invoke(null, base);
        return Objects.requireNonNull(result, "ModelEngine returned no modeled entity");
    }

    Object createActive(String assetId) throws ReflectiveOperationException {
        Object result = createActiveModel.invoke(null, assetId);
        return Objects.requireNonNull(result, "ModelEngine returned no active model");
    }

    /**
     * Attaches the model to the carrier.
     *
     * <p>{@code addModel} returns the model it <em>replaced</em>, not the one it added — an
     * {@code Optional} holding whatever was previously registered under the same blueprint name. For a pet
     * being spawned there is no previous model, so success is {@code Optional.empty()}.
     *
     * <p>This adapter read that empty as a rejection and threw. Every ModelEngine pet therefore failed at
     * spawn, the resolver caught it and fell back to the built-in head renderer, and the operator was told
     * "ModelEngine rejected the active model" — which is why a MODELENGINE pet rendered as a head, faced
     * the wrong way, and looked as though its provider had been ignored. One inverted condition accounted
     * for the whole cluster of symptoms.
     *
     * <p>A genuine refusal is not silent: {@code addModel} only bails early when a plugin cancels the
     * {@code AddModelEvent}, and the other failure modes throw. So there is nothing to detect here beyond
     * letting the call return.
     */
    void attach(Object modeled, Object active) throws ReflectiveOperationException {
        addModel.invoke(modeled, active, true);
        setBaseEntityVisible.invoke(modeled, false);
    }

    void scale(Object active, double value) throws ReflectiveOperationException {
        setScale.invoke(active, value);
    }

    void replace(Object modeled, Object previous, String previousId, Object next) throws ReflectiveOperationException {
        removeModel.invoke(modeled, previousId);
        destroyActiveModel.invoke(previous);
        attach(modeled, next);
    }

    void destroy(Object modeled, Object active) throws ReflectiveOperationException {
        try {
            destroyActiveModel.invoke(active);
        } finally {
            destroyModeledEntity.invoke(modeled);
        }
    }
}
