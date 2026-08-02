package io.github.salyvn.omnipet.core.incubation;

@FunctionalInterface
public interface HatchEventListener {
    void onHatchEvent(HatchEvent event) throws Exception;
}
