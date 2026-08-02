package io.github.salyvn.omnipet.core.skill;

public interface SkillProvider {
    String providerId();

    SkillCatalogSnapshot catalog();

    SkillCastResult cast(SkillCastRequest request);
}
