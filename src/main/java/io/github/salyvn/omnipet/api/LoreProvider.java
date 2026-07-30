package io.github.salyvn.omnipet.api;

import java.util.List;

import net.kyori.adventure.text.Component;

/**
 * <p>Include this interface in your implementation of {@link PetComponent}.</p>
 */
public interface LoreProvider {
	List<Component> provideLore();
}
