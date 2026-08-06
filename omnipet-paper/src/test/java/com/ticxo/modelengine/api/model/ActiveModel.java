package com.ticxo.modelengine.api.model;

/** Stand-in for ModelEngine's {@code ActiveModel}, shaped to the real R4 API. */
public final class ActiveModel {
    private final String blueprintName;
    private double scale = 1;
    private boolean destroyed;

    public ActiveModel(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public String blueprintName() { return blueprintName; }

    public void setScale(double value) { scale = value; }

    public double scale() { return scale; }

    public void destroy() { destroyed = true; }

    public boolean destroyed() { return destroyed; }
}
