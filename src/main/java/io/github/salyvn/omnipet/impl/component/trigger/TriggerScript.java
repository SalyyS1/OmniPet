package io.github.salyvn.omnipet.impl.component.trigger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.utils.ParsedExpr;

public interface TriggerScript {
	String type();
	TriggerStatement script();
	ParsedExpr cooldown();
	ParsedExpr precondition();
	List<String> lore();

	Map<String, MapCodec<? extends TriggerScript>> TYPES = Map.ofEntries();
	MapCodec<TriggerScript> MAP_CODEC = Codec.STRING.dispatchMap(
			"type",
			TriggerScript::type,
			id -> TYPES.containsKey(id) ? TYPES.get(id) : Generic.mapCodec(id));
	Codec<TriggerScript> CODEC = MAP_CODEC.codec();

	// Moved to class because we don't want hashCode() to compute everything inside
	class Generic implements TriggerScript {
		public static MapCodec<Generic> mapCodec(String type) {
			return RecordCodecBuilder.mapCodec(i -> i.group(
					ParsedExpr.CODEC.optionalFieldOf("cooldown").forGetter(g -> Optional.ofNullable(g.cooldown)),
					ParsedExpr.CODEC.optionalFieldOf("precondition").forGetter(g -> Optional.ofNullable(g.precondition)),
					Codec.list(Codec.STRING).optionalFieldOf("lore", Collections.emptyList()).forGetter(Generic::lore),
					TriggerStatement.CODEC.fieldOf("script").forGetter(Generic::script))
					.apply(i, (cooldown, precond, lore, script) -> new Generic(type, cooldown.orElse(null), precond.orElse(null), lore, script)));
		}

		private String type;
		private ParsedExpr cooldown;
		private ParsedExpr precondition;
		private List<String> lore;
		private TriggerStatement script;

		public Generic(String type, ParsedExpr cooldown, ParsedExpr precondition, List<String> lore, TriggerStatement script) {
			this.type = type;
			this.cooldown = cooldown;
			this.precondition = precondition;
			this.lore = lore;
			this.script = script;
		}

		@Override
		public String type() { return type; }

		@Override
		public ParsedExpr cooldown() { return cooldown; }

		@Override
		public ParsedExpr precondition() { return precondition; }

		@Override
		public List<String> lore() { return lore; }

		@Override
		public TriggerStatement script() { return script; }
	}
}
