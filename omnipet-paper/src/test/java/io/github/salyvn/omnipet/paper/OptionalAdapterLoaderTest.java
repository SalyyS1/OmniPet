package io.github.salyvn.omnipet.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OptionalAdapterLoaderTest {
    private final OptionalAdapterLoader loader = new OptionalAdapterLoader();

    @Test
    void absentProbeFailsClosed() {
        var result = loader.load("not.present.Vendor", GoodAdapter.class.getName(), getClass().getClassLoader());
        assertEquals(OptionalAdapterLoader.Status.UNAVAILABLE, result.status());
    }

    @Test
    void reflectiveAndLinkageFailuresDoNotEscape() {
        var reflection = loader.load(String.class.getName(), PrivateAdapter.class.getName(), getClass().getClassLoader());
        assertEquals(OptionalAdapterLoader.Status.INCOMPATIBLE, reflection.status());

        var linkage = loader.load(String.class.getName(), BrokenAdapter.class.getName(), getClass().getClassLoader());
        assertEquals(OptionalAdapterLoader.Status.INCOMPATIBLE, linkage.status());
    }

    public static final class GoodAdapter {}

    private static final class PrivateAdapter {
        private PrivateAdapter() {}
    }

    public static final class BrokenAdapter {
        static {
            if (true) throw new NoClassDefFoundError("missing vendor ABI");
        }
    }
}
