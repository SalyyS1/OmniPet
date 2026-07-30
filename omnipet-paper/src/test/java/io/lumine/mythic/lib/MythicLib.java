package io.lumine.mythic.lib;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class MythicLib {
    private static final MythicLib INSTANCE = new MythicLib();
    private static boolean handlerFailure;
    private final StatManager stats = new StatManager();

    public static MythicLib inst() {
        return INSTANCE;
    }

    public StatManager getStats() {
        return stats;
    }

    public static void setHandlerFailure(boolean value) {
        handlerFailure = value;
    }

    public static final class StatManager {
        public Set<String> getRegisteredStats() {
            return new LinkedHashSet<>(Set.of("HEALTH_REGEN", "ATTACK_DAMAGE"));
        }

        public Optional<StatHandler> getHandler(String id) {
            if (handlerFailure) throw new IllegalStateException("handler ABI failure");
            return Optional.of(new StatHandler(id));
        }
    }

    public static final class StatHandler {
        private final String id;

        private StatHandler(String id) {
            this.id = id;
        }

        public String getStat() {
            return id;
        }
    }
}
