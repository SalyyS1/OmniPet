package io.github.salyvn.omnipet.core.skill;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Commits stamina and cooldown only after a successful provider cast. */
public final class ActiveSkillDispatcher {
    public SkillExecutionResult execute(
            SkillBinding binding,
            SkillExecutionContext context,
            SkillProvider provider,
            SkillPrecondition precondition) {
        Objects.requireNonNull(binding, "skill binding");
        Objects.requireNonNull(context, "skill execution context");
        Objects.requireNonNull(precondition, "skill precondition");
        if (provider == null || !binding.provider().equalsIgnoreCase(provider.providerId())) {
            return unchanged(SkillExecutionResult.Status.PROVIDER_UNAVAILABLE, context, null,
                    "skill provider is unavailable");
        }
        SkillCatalogSnapshot catalog;
        try {
            catalog = Objects.requireNonNull(provider.catalog(), "skill provider catalog");
        } catch (RuntimeException | LinkageError failure) {
            return unchanged(SkillExecutionResult.Status.PROVIDER_UNAVAILABLE, context, null, detail(failure));
        }
        if (!catalog.health().available()) {
            return unchanged(SkillExecutionResult.Status.PROVIDER_UNAVAILABLE, context, null, catalog.health().detail());
        }
        if (!catalog.contains(binding.skillId())) {
            return unchanged(SkillExecutionResult.Status.INVALID_SKILL, context, null,
                    "skill is not present in provider catalog");
        }
        long deadline = context.cooldownDeadlines().getOrDefault(binding.bindingId(), 0L);
        if (deadline > context.nowEpochMillis()) {
            return unchanged(SkillExecutionResult.Status.COOLDOWN, context, null, "skill is cooling down");
        }
        if (context.stamina() < binding.staminaCost()) {
            return unchanged(SkillExecutionResult.Status.INSUFFICIENT_RESOURCE, context, null,
                    "not enough stamina");
        }
        try {
            if (!precondition.test(context)) {
                return unchanged(SkillExecutionResult.Status.PRECONDITION_FALSE, context, null,
                        "skill precondition is false");
            }
        } catch (RuntimeException | LinkageError failure) {
            return unchanged(SkillExecutionResult.Status.FAILED, context, null, detail(failure));
        }
        if (context.chanceRoll() >= binding.chance()) {
            return unchanged(SkillExecutionResult.Status.CHANCE_MISS, context, null, "skill chance did not pass");
        }

        SkillCastResult cast;
        try {
            cast = Objects.requireNonNull(provider.cast(new SkillCastRequest(
                    context.actionId(), context.actorId(), context.ownerId(), context.petInstanceId(),
                    binding.skillId(), binding.targetPolicy(), context.providerContext())),
                    "skill provider result");
        } catch (RuntimeException | LinkageError failure) {
            return unchanged(SkillExecutionResult.Status.FAILED, context, null, detail(failure));
        }
        if (!cast.succeeded()) {
            return unchanged(SkillExecutionResult.Status.FAILED, context, cast, cast.detail());
        }

        Map<String, Long> cooldowns = new LinkedHashMap<>(context.cooldownDeadlines());
        long cooldownMillis;
        try {
            cooldownMillis = binding.cooldown().toMillis();
        } catch (ArithmeticException overflow) {
            cooldownMillis = Long.MAX_VALUE;
        }
        long nextDeadline = saturatingAdd(context.nowEpochMillis(), cooldownMillis);
        if (cooldownMillis > 0) cooldowns.put(binding.bindingId(), nextDeadline);
        return new SkillExecutionResult(
                SkillExecutionResult.Status.SUCCESS,
                context.stamina() - binding.staminaCost(),
                cooldowns,
                cast,
                "skill cast succeeded");
    }

    private static SkillExecutionResult unchanged(
            SkillExecutionResult.Status status,
            SkillExecutionContext context,
            SkillCastResult cast,
            String detail) {
        return new SkillExecutionResult(status, context.stamina(), context.cooldownDeadlines(), cast, detail);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
        return left + right;
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
