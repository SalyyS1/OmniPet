package com.ticxo.modelengine.api.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stand-in for ModelEngine's {@code ModeledEntity}, reproducing the one contract that mattered.
 *
 * <p>{@code addModel} returns the model it <em>replaced</em>, not the one it added. For a pet being spawned
 * there is nothing to replace, so success is {@code Optional.empty()} — and reading that empty as a
 * rejection is what made every ModelEngine pet fall back to a player head.
 */
public final class ModeledEntity {
    private final Map<String, ActiveModel> models = new LinkedHashMap<>();
    private boolean baseEntityVisible = true;
    private boolean destroyed;

    /** Returns the previously registered model for this blueprint, or empty when there was none. */
    public Optional<ActiveModel> addModel(ActiveModel model, boolean overrideHitbox) {
        Optional<ActiveModel> previous = removeModel(model.blueprintName());
        models.put(model.blueprintName(), model);
        return previous;
    }

    /** Detaches without destroying, exactly as the real one does. */
    public Optional<ActiveModel> removeModel(String blueprintName) {
        return Optional.ofNullable(models.remove(blueprintName));
    }

    public void setBaseEntityVisible(boolean value) { baseEntityVisible = value; }

    public boolean isBaseEntityVisible() { return baseEntityVisible; }

    public void destroy() { destroyed = true; }

    public boolean destroyed() { return destroyed; }

    /** The models currently attached, so a test can assert what actually landed. */
    public Map<String, ActiveModel> attached() { return Map.copyOf(models); }
}
