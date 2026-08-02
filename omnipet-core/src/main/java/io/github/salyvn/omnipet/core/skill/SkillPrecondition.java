package io.github.salyvn.omnipet.core.skill;

@FunctionalInterface
public interface SkillPrecondition {
    boolean test(SkillExecutionContext context);

    static SkillPrecondition always() {
        return ignored -> true;
    }
}
