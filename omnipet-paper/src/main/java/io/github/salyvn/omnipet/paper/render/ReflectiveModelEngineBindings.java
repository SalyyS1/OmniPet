package io.github.salyvn.omnipet.paper.render;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

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

    void attach(Object modeled, Object active) throws ReflectiveOperationException {
        Object result = addModel.invoke(modeled, active, true);
        if (result instanceof Optional<?> optional && optional.isEmpty()) {
            throw new IllegalStateException("ModelEngine rejected the active model");
        }
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
