package io.github.salyvn.omnipet.paper;

public final class OptionalAdapterLoader {
    public LoadResult load(String probeClassName, String adapterClassName, ClassLoader loader) {
        try {
            Class.forName(probeClassName, false, loader);
            Object adapter = Class.forName(adapterClassName, true, loader).getDeclaredConstructor().newInstance();
            return new LoadResult(Status.AVAILABLE, adapter, null);
        } catch (ClassNotFoundException error) {
            return new LoadResult(Status.UNAVAILABLE, null, error);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return new LoadResult(Status.INCOMPATIBLE, null, error);
        }
    }

    public enum Status {
        AVAILABLE,
        UNAVAILABLE,
        INCOMPATIBLE
    }

    public record LoadResult(Status status, Object adapter, Throwable failure) {}
}
