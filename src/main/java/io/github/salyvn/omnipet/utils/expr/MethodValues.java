package io.github.salyvn.omnipet.utils.expr;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.nahkd123.tinyexpr.impl.MethodValue;

public class MethodValues {
	private MethodValues() {}

	public static <A, R> MethodValue of(Function<A, R> f, Class<A> a, Class<R> r) {
		try {
			MethodHandle handle = MethodHandles
					.publicLookup()
					.findVirtual(Function.class, "apply", MethodType.genericMethodType(1));
			MethodType signature = MethodType.methodType(r, a);
			handle = handle.bindTo(f).asType(signature);
			return new MethodValue(handle, signature);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static MethodValue ofVoid(Runnable f) {
		try {
			MethodHandle handle = MethodHandles
					.publicLookup()
					.findVirtual(Runnable.class, "run", MethodType.methodType(void.class));
			MethodType signature = MethodType.methodType(void.class);
			handle = handle.bindTo(f).asType(signature);
			return new MethodValue(handle, signature);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static <A> MethodValue ofVoid(Consumer<A> f, Class<A> a) {
		try {
			MethodHandle handle = MethodHandles
					.publicLookup()
					.findVirtual(Consumer.class, "accept", MethodType.methodType(void.class, Object.class));
			MethodType signature = MethodType.methodType(void.class, a);
			handle = handle.bindTo(f).asType(signature);
			return new MethodValue(handle, signature);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
