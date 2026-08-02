package io.github.salyvn.omnipet.core.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ActiveSkillDispatcherTest {
    private final ActiveSkillDispatcher dispatcher = new ActiveSkillDispatcher();
    private final SkillBinding binding = new SkillBinding(
            "flame_burst", "MYTHICMOBS", "FlameBurst", SkillTrigger.ACTIVE,
            Duration.ofSeconds(5), 1, 25, SkillTargetPolicy.LOOK_TARGET, true);

    @Test
    void successfulCastCommitsCooldownAndStamina() {
        SkillExecutionResult result = dispatcher.execute(binding, context(100, 0, 50, 0), provider(true),
                SkillPrecondition.always());

        assertEquals(SkillExecutionResult.Status.SUCCESS, result.status());
        assertEquals(25, result.stamina());
        assertEquals(5_100L, result.cooldownDeadlines().get(binding.bindingId()));
    }

    @Test
    void providerFailureDoesNotConsumeReservedState() {
        SkillExecutionContext context = context(100, 0, 50, 0);
        SkillExecutionResult rejected = dispatcher.execute(binding, context, provider(false), SkillPrecondition.always());
        SkillExecutionResult linkage = dispatcher.execute(binding, context, new TestProvider() {
            @Override public SkillCastResult cast(SkillCastRequest request) {
                throw new NoClassDefFoundError("vendor ABI");
            }
        }, SkillPrecondition.always());

        assertEquals(SkillExecutionResult.Status.FAILED, rejected.status());
        assertEquals(50, rejected.stamina());
        assertEquals(Map.of(), rejected.cooldownDeadlines());
        assertEquals(SkillExecutionResult.Status.FAILED, linkage.status());
        assertEquals(50, linkage.stamina());
    }

    @Test
    void preconditionCooldownChanceAndResourceChecksRunBeforeProvider() {
        CountingProvider provider = new CountingProvider();

        assertEquals(SkillExecutionResult.Status.PRECONDITION_FALSE,
                dispatcher.execute(binding, context(100, 0, 50, 0), provider, ignored -> false).status());
        assertEquals(SkillExecutionResult.Status.COOLDOWN,
                dispatcher.execute(binding, context(100, 200, 50, 0), provider, SkillPrecondition.always()).status());
        assertEquals(SkillExecutionResult.Status.INSUFFICIENT_RESOURCE,
                dispatcher.execute(binding, context(100, 0, 10, 0), provider, SkillPrecondition.always()).status());
        SkillBinding chanceBinding = new SkillBinding(
                "flame_chance", "MYTHICMOBS", "FlameBurst", SkillTrigger.ACTIVE,
                Duration.ofSeconds(5), 0.5, 25, SkillTargetPolicy.LOOK_TARGET, true);
        assertEquals(SkillExecutionResult.Status.CHANCE_MISS,
                dispatcher.execute(chanceBinding, context(100, 0, 50, 0.75), provider,
                        SkillPrecondition.always()).status());
        assertEquals(0, provider.calls);
    }

    @Test
    void missingOrUnhealthyCatalogFailsClosed() {
        SkillProvider unhealthy = new TestProvider() {
            @Override public SkillCatalogSnapshot catalog() {
                return new SkillCatalogSnapshot(1,
                        new SkillProviderHealth(SkillProviderHealth.Status.QUARANTINED, "broken ABI"), Set.of());
            }
        };
        SkillProvider missing = new TestProvider() {
            @Override public SkillCatalogSnapshot catalog() {
                return new SkillCatalogSnapshot(1,
                        new SkillProviderHealth(SkillProviderHealth.Status.AVAILABLE, "ok"), Set.of("Other"));
            }
        };

        assertEquals(SkillExecutionResult.Status.PROVIDER_UNAVAILABLE,
                dispatcher.execute(binding, context(100, 0, 50, 0), unhealthy, SkillPrecondition.always()).status());
        assertEquals(SkillExecutionResult.Status.INVALID_SKILL,
                dispatcher.execute(binding, context(100, 0, 50, 0), missing, SkillPrecondition.always()).status());
    }

    private static SkillExecutionContext context(long now, long deadline, double stamina, double chanceRoll) {
        return new SkillExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                now, stamina, chanceRoll,
                deadline == 0 ? Map.of() : Map.of("flame_burst", deadline),
                Map.of());
    }

    private static SkillProvider provider(boolean success) {
        return new TestProvider() {
            @Override public SkillCastResult cast(SkillCastRequest request) {
                return new SkillCastResult(
                        success ? SkillCastResult.Status.SUCCESS : SkillCastResult.Status.FAILED,
                        success ? "ok" : "cast failed");
            }
        };
    }

    private static class TestProvider implements SkillProvider {
        @Override public String providerId() { return "MYTHICMOBS"; }
        @Override public SkillCatalogSnapshot catalog() {
            return new SkillCatalogSnapshot(1,
                    new SkillProviderHealth(SkillProviderHealth.Status.AVAILABLE, "ok"), Set.of("FlameBurst"));
        }
        @Override public SkillCastResult cast(SkillCastRequest request) {
            return new SkillCastResult(SkillCastResult.Status.SUCCESS, "ok");
        }
    }

    private static final class CountingProvider extends TestProvider {
        private int calls;
        @Override public SkillCastResult cast(SkillCastRequest request) {
            calls++;
            return super.cast(request);
        }
    }
}
