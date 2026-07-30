package io.github.salyvn.omnipet.api;

public sealed interface ScopeSource {
	record Global() implements ScopeSource {}
	record Player(org.bukkit.entity.Player player) implements ScopeSource {}
	record Pet(io.github.salyvn.omnipet.api.Pet pet) implements ScopeSource {}
}
