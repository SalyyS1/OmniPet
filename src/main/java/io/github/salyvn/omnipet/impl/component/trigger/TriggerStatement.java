package io.github.salyvn.omnipet.impl.component.trigger;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.utils.ParsedExpr;
import io.github.nahkd123.tinyexpr.Value;

public sealed interface TriggerStatement {
	void trigger(Function<String, Value> context);

	record Eval(ParsedExpr expr) implements TriggerStatement {
		public static final Codec<Eval> CODEC = ParsedExpr.CODEC.xmap(Eval::new, Eval::expr);

		@Override
		public void trigger(Function<String, Value> context) {
			expr.eval(context);
		}
	}

	record Block(List<TriggerStatement> content) implements TriggerStatement {
		public static Codec<Block> codec(Codec<TriggerStatement> stmt) {
			return Codec.list(stmt).xmap(Block::new, Block::content);
		}

		@Override
		public void trigger(Function<String, Value> context) {
			for (TriggerStatement s : content) s.trigger(context);
		}
	}

	record If(ParsedExpr condition, TriggerStatement ifTrue, TriggerStatement ifFalse) implements TriggerStatement {
		public If(ParsedExpr condition, Optional<TriggerStatement> ifTrue, Optional<TriggerStatement> ifFalse) {
			this(condition, ifTrue.orElse(null), ifFalse.orElse(null));
		}

		public static Codec<If> codec(Codec<TriggerStatement> stmt) {
			return RecordCodecBuilder.create(i -> i.group(
					ParsedExpr.CODEC.fieldOf("if").forGetter(If::condition),
					stmt.optionalFieldOf("onTrue").forGetter(s -> Optional.ofNullable(s.ifTrue)),
					stmt.optionalFieldOf("onFalse").forGetter(s -> Optional.ofNullable(s.ifFalse)))
					.apply(i, If::new));
		}

		@Override
		public void trigger(Function<String, Value> context) {
			if (condition.eval(context).unwrapAs(boolean.class)) {
				if (ifTrue != null) ifTrue.trigger(context);
			} else {
				if (ifFalse != null) ifFalse.trigger(context);
			}
		}
	}

	Codec<TriggerStatement> CODEC = Codec.<TriggerStatement>recursive("TriggerStatement", self -> Codec
			.either(
					Eval.CODEC,
					Codec.either(Block.codec(self), If.codec(self)).<TriggerStatement>xmap(
							e -> Either.unwrap(e),
							v -> v instanceof Block b ? Either.left(b) : Either.right((If) v)))
			.<TriggerStatement>xmap(
					e -> Either.unwrap(e),
					v -> v instanceof Eval e ? Either.left(e) : Either.right(v)));
}
