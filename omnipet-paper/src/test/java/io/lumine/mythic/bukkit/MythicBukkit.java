package io.lumine.mythic.bukkit;

import java.util.Set;

public final class MythicBukkit {
    private static final MythicBukkit INSTANCE = new MythicBukkit();
    private final SkillManager skills = new SkillManager();
    private final APIHelper helper = new APIHelper();

    public static MythicBukkit inst() {
        return INSTANCE;
    }

    public static void reset() {
        INSTANCE.helper.fail = false;
        INSTANCE.helper.caster = null;
    }

    public SkillManager getSkillManager() {
        return skills;
    }

    public APIHelper getAPIHelper() {
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

        public boolean castSkill(Object caster, String skill) {
            if (fail) throw new IllegalStateException("simulated provider failure");
            this.caster = caster;
            return !skill.equals("rejected");
        }
    }
}
