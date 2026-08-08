package io.lumine.mythic.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class MythicBukkit {
    private static final MythicBukkit INSTANCE = new MythicBukkit();
    private final SkillManager skills = new SkillManager();
    private final APIHelper helper = new APIHelper();

    public static MythicBukkit inst() {
        return INSTANCE;
    }

    /** Set to fail {@link #getAPIHelper()} itself, which is an adapter fault rather than a skill's. */
    public boolean failHelper;

    public static void reset() {
        INSTANCE.failHelper = false;
        INSTANCE.helper.fail = false;
        INSTANCE.helper.caster = null;
        INSTANCE.helper.entityTargets = null;
        INSTANCE.helper.power = 0;
        INSTANCE.helper.usedTargetedOverload = false;
    }

    public SkillManager getSkillManager() {
        return skills;
    }

    public APIHelper getAPIHelper() {
        if (failHelper) throw new IllegalStateException("simulated adapter failure");
        return helper;
    }

    public static final class SkillManager {
        public Set<String> getSkillNames() {
            return Set.of("ember_burst", "healing_wave");
        }
    }

    public static final class APIHelper {
        public boolean fail;
        public Object caster;
        public List<Object> entityTargets;
        public float power;
        public boolean usedTargetedOverload;

        public boolean castSkill(Object caster, String skill) {
            if (fail) throw new IllegalStateException("simulated provider failure");
            this.caster = caster;
            return !skill.equals("rejected");
        }

        /** Mirrors the real seven-argument overload, which is the only one that accepts targets. */
        public boolean castSkill(
                Object caster,
                String skill,
                Object trigger,
                org.bukkit.Location origin,
                Collection<Object> entityTargets,
                Collection<org.bukkit.Location> locationTargets,
                float power) {
            if (fail) throw new IllegalStateException("simulated provider failure");
            this.caster = caster;
            this.entityTargets = List.copyOf(entityTargets);
            this.power = power;
            this.usedTargetedOverload = true;
            return !skill.equals("rejected");
        }
    }
}
