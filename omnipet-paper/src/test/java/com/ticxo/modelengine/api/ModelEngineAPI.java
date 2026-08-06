package com.ticxo.modelengine.api;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.entity.Entity;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

/** Stand-in for ModelEngine's {@code ModelEngineAPI}, matching the static shape the adapter reflects on. */
public final class ModelEngineAPI {
    private static final Set<String> LOADED = new LinkedHashSet<>();

    private ModelEngineAPI() {}

    /** Registers a blueprint as loaded, mirroring a model the server has on disk. */
    public static void loadBlueprint(String id) {
        LOADED.add(id);
    }

    public static void reset() {
        LOADED.clear();
    }

    /** Null for a model the server does not have, which is what the adapter checks. */
    public static Object getBlueprint(String id) {
        return LOADED.contains(id) ? id : null;
    }

    public static ModeledEntity createModeledEntity(Entity base) {
        return new ModeledEntity();
    }

    /** Throws for an unknown model, exactly as the real one does. */
    public static ActiveModel createActiveModel(String modelId) {
        if (!LOADED.contains(modelId)) {
            throw new RuntimeException("Error while creating ActiveModel. Unknown model: " + modelId);
        }
        return new ActiveModel(modelId);
    }
}
