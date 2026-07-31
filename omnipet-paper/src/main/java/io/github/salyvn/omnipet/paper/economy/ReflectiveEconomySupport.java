package io.github.salyvn.omnipet.paper.economy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

final class ReflectiveEconomySupport {
    private ReflectiveEconomySupport() {}

    static Method binaryAmountMethod(
            Class<?> type,
            String name,
            Class<?> subjectType,
            Class<?> numericType) {
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == 2)
                .filter(method -> method.getParameterTypes()[0] == subjectType)
                .filter(method -> method.getParameterTypes()[1] == numericType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("provider method is missing: " + name));
    }

    static Method unaryMethod(Class<?> type, String name, Class<?> subjectType) {
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0] == subjectType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("provider method is missing: " + name));
    }

    static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }

    static String detail(Throwable failure, String fallback) {
        String message = failure.getMessage();
        String detail = message == null || message.isBlank() ? fallback : fallback + ": " + message;
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
