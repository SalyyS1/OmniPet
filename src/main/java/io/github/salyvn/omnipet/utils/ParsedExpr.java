package io.github.salyvn.omnipet.utils;

import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.github.nahkd123.tinyexpr.Expr;
import io.github.nahkd123.tinyexpr.ExprParser;
import io.github.nahkd123.tinyexpr.Value;
import io.github.nahkd123.tinyexpr.impl.DoubleValue;

public record ParsedExpr(String content, Expr expr) {
	private static final Codec<ParsedExpr> ALTERNATIVES = Codec.either(Codec.DOUBLE, Codec.INT)
			.xmap(either -> {
				double v = either.left().orElseGet(() -> either.right().get().doubleValue());
				return new ParsedExpr("", new Expr.Const(new DoubleValue(v)));
			}, v -> { throw new RuntimeException("reached the unreachable"); });

	public static final Codec<ParsedExpr> CODEC = Codec.either(
			Codec.STRING.comapFlatMap(content -> {
				try {
					return DataResult.success(new ParsedExpr(content, ExprParser.parse(content)));
				} catch (IllegalArgumentException | IllegalStateException e) {
					return DataResult.error(e::getLocalizedMessage);
					}
				}, ParsedExpr::content),
			ALTERNATIVES)
			.xmap(either -> either.left().orElseGet(either.right()::get), Either::left);

	public ParsedExpr(String content) {
		this(content, ExprParser.parse(content));
	}

	public Value eval(Function<String, Value> vars) {
		return expr.eval(vars);
	}

	@Override
	public final String toString() {
		return "ParsedExpr(%s)".formatted(content);
	}
}
