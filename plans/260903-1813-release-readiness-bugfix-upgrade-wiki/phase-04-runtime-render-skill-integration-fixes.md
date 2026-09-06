---
phase: 4
title: "Runtime, render, skill integration fixes"
status: pending
priority: P1
effort: "2.5d"
dependencies: [1]
---

# Phase 4: Runtime, render, skill integration fixes

## Context Links

- Findings source: `plans/reports/from-scout-runtime-to-planner-runtime-render-skill-defect-review-report.md`

## Overview

Fix the two runtime P1s — MythicMobs kill identity bound to the wrong classloader (feature silently dead on every server) and the trigger listener fanning disk-backed tasks to every player on every event — then unify quarantine policy across all five reflective vendor bridges and clear the render/skill P2 hotspots.

## Key Insights

- `ReflectiveMythicMobsIdentity.java:42` binds via the killed entity's (NMS) classloader → `ClassNotFoundException` → permanently `unavailable`, never logged, `refresh()` never called. Every MythicMobs kill is priced as vanilla. Correct pattern already exists in `PaperMythicMobsSkillContext.java:24`.
- `SkillTriggerListener` gates only drop/swap/interval on `skills.hasTrigger`; sneak/sprint/jump/damage handlers submit a queue task + player-state read per event per online player (`SkillTriggerListener.java:76-176`).
- Recurring theme: every reflective bridge conflates "one bad input" with "vendor ABI broken" — one typo quarantines the whole integration (renderer `PaperModelEngineRenderer.java:100-137`, buffs `ReflectiveMythicLibBuffPort.java:103-106`, animations, skills). While quarantined the resolver rebuilds the renderer reflectively every resolve.
- `Attribute.GENERIC_MAX_HEALTH` at `SkillTriggerListener.java:197` was the only version-drift hazard found in range (checked: `feedback/SoundResolver.java:17,48,53` already goes through `Registry.SOUNDS`; `Particle.valueOf` in `config/OmniPetConfigLoader` and `feedback/FeedbackSettings.java:82` is safe across 1.21.x). **Settled 2026-09-04 with evidence:** the `compatibility` job (`omnipet-paper/build.gradle.kts:54-69`, `.github/workflows/build.yml:32-48`) had been red since 2026-08-06 on every matrix point above `1.21-R0.1-SNAPSHOT` — `cannot find symbol GENERIC_MAX_HEALTH`, because the 1.21.11 API's `Attribute` is an interface exposing `MAX_HEALTH` only. That is the P1 class (a shipped JAR dies with `NoSuchFieldError` on the first damage event for an owner with a low-health binding on a newer server), so Phase 1 lands the one-line `player.getMaxHealth()` hotfix and this phase starts from a green matrix.

  For any further attribute work here: keep `player.getMaxHealth()` as the single path. **Do not** use `Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"))` — the key was `generic.max_health` before the 1.21.2/1.21.3 flattening, so on the 1.21.0/1.21.1 floor the lookup returns null and `attribute == null ? 20` (`SkillTriggerListener.java:198`) silently treats every player as 20 max HP, mis-thresholding the low-health trigger instead of erroring.

## Requirements

- Functional: MythicMobs kill EXP works after `/mm reload`, PlugMan re-enable, and on first kill; passive triggers cost ~zero for players without matching bindings; one bad pet/skill/buff input degrades only that input.
- Non-functional: no reflection (`getMethod`/`getMethods`) on per-cast/per-tick hot paths; failure logs deduplicated.

## Architecture

- **Quarantine policy split** (one shared convention, applied to `ReflectiveModelEngineBindings`, `ReflectiveModelEngineAnimationBindings`, `ReflectiveMythicMobsSkillProvider`, `ReflectiveMythicLibBuffPort`, `ReflectiveMythicMobsIdentity`): `ReflectiveOperationException`/`LinkageError`/`ClassCastException` at bind time → bridge quarantine with `unavailableDetail()` logged once; any exception attributable to one pet/skill/buff/model → per-input failure memo reported through the problems sink, bridge stays healthy.
- **Identity fix:** bind via MythicMobs plugin classloader; `refresh()` wired to `MythicMobsSkillLifecycleListener` enable/reload and `/pet admin reload`; unit test binds with a loader that cannot see the class.
- **Trigger gating:** every handler checks `skills.hasTrigger(playerId, trigger)` (allocation-free index, refreshed on join/active-set change) before submitting.
- **MethodHandle caching:** resolve `castSkill` overloads (param-type match, not arity), `isMythicMob`, ModelEngine accessors once per refresh epoch; cast origin = pet position.
- **Failure sink:** shared `RateLimitedWarnings` (key: owner/pet/stage/detail) used by runtime failure sink, buff coordinator, renderer fallback, reindex swallow — and the idle-play particle path, which `PaperRuntimeOwnerEngine.emitParticle` reports under the `MOVEMENT` stage every 10 ticks per playing pet if the particle backend throws; after N consecutive update failures a handle is retired so activation respawns it (self-heal).
- **Teleport handling:** same-world `PlayerTeleportEvent` relocates carriers instead of despawn/respawn; kick+quit double-fire collapsed. Verified while planning: `PlayerStorageLifecycleListener.java:60-70` runs the identical `controller.release` + `ownerQuit` pair for both events, and `PaperPetRuntimeCoordinator.ownerQuit:157-166` is already idempotent by construction (map remove, set add, cleanup). So only `controller.release` needs an idempotency check — do that first; if it is clean, drop `onKick` as redundant rather than adding a guard flag. ModelEngine per-tick triple teleport gated on `CarrierMotion` position/facing delta.

## Related Code Files

- Modify: `omnipet-paper/.../progression/ReflectiveMythicMobsIdentity.java`, `skill/MythicMobsSkillLifecycleListener.java` (also unregister on vendor disable), `skill/SkillTriggerListener.java` (gates, MAX_HEALTH strategy, projectile shooter unwrap), `skill/PaperActiveSkillController.java` (per-pet MAX_BINDINGS, reindex log-once, ThreadLocalRandom), `skill/ReflectiveMythicMobsSkillProvider.java`, `skill/PaperSkillTargetResolver.java` (exclude ArmorStand/Display/Interaction + handle UUIDs, sphere radius filter)
- Modify: `omnipet-paper/.../render/PaperModelEngineRenderer.java`, `PaperModelEngineRendererResolver.java` (no rebuild while quarantined), `ReflectiveModelEngineAnimationBindings.java`, `PaperHeadRendererHandle.java` (the `0.02` speed-delta test in `tiltDiffers:86-88` is the tilt threshold; the `setTransformation` it triggers is `BukkitPaperHeadRendererBackend.java:212`), `PaperHeadRenderer.java` (collapse retire duplication), `render/CarrierMotion.java` (yaw wrap)

<!-- Updated: Validation Session 2 - tilt threshold, teleport handler, and skill-provider line references corrected -->
- Corrected references: the teleport path is `PlayerStorageLifecycleListener.onTeleport:72-75` → `PaperPetRuntimeCoordinator.ownerWorldChanged:168` (named for world change, invoked for every teleport); the per-cast reflection in `ReflectiveMythicMobsSkillProvider` is `findTargetedCast` invoked at `:113` with the `getMethods()` scans at `:198` and `:213`.
- Modify: `omnipet-paper/.../buff/ReflectiveMythicLibBuffPort.java` (per-buff validation, modifier diffing), `buff/PaperOwnerBuffCoordinator.java` (formatting, warnOnce concurrency, dead ctor)
- Modify: `omnipet-paper/.../runtime/PaperRuntimeBootstrap.java` (rate-limited sink), `runtime/PaperPetRuntimeCoordinator.java` (teleport relocation, kick/quit), `player/PlayerStorageLifecycleListener.java`
- Modify: `omnipet-paper/.../OmniPetPlugin.java` (SEVERE with stack trace, catch LinkageError in onEnable/reload, prune orphaned Javadoc), `paper-plugin.yml` (dedupe permissions; description moves in Phase 10)
- Create: `omnipet-paper/.../runtime/RateLimitedWarnings.java` (shared), pet-entity registry surface on `InteractionIndex` (all carrier/plate/interaction UUIDs) for the target resolver
- Delete: `omnipet-paper/.../OptionalAdapterLoader.java` (dead), `progression/KillExperienceListener.drain()` (dead)

## Implementation Steps

1. Identity classloader fix + refresh wiring + once-logged detail + loader-blind unit test.
2. `hasTrigger` gate on every handler; perf test asserts no task submitted for a player with no matching binding. Same step: unwrap `Projectile#getShooter` on the damager (`SkillTriggerListener.java:132`) so `ON_ATTACK`/`ON_DAMAGE_TAKEN` fire for bow and trident hits — today only `ON_KILL` works for ranged, because it reads `getKiller()` instead.
3. Quarantine policy split across the five bridges + resolver rebuild guard; tests: bad model ID → that pet falls back, others render; bad modifierType → that buff skipped, others apply; `getAPIHelper` throw → bridge quarantined once. Same step: stop re-registering unchanged MythicLib modifiers on every reconcile (`ReflectiveMythicLibBuffPort.java:83-91`) — diff by deterministic UUID and touch only what changed, or every activation change recomputes every owner's stats.
3b. Cap skill bindings **per pet**, not across all of an owner's pets (`PaperActiveSkillController.java:224`): the current global `.limit(MAX_BINDINGS)` silently drops a third pet's bindings, which reads as "that pet's skills don't work". Log the reindex swallow once per owner (`:226-228`), switch `Math.random()` to `ThreadLocalRandom` (`:319`), and document the `runMain`-while-disabled pending reservation and its admin `rollback` recovery (`:557-563`).
4. MethodHandle caching at refresh epoch; param-type overload match; pet-origin cast.
5. `RateLimitedWarnings` + handle retire-after-N self-heal; dedupe test.
6. Target resolver exclusions + sphere filter + pet-entity registry.
7. Teleport relocation (split `ownerWorldChanged` into a same-world relocate and a cross-world rebuild, or branch on `from.getWorld() == to.getWorld()` in the listener) + kick/quit idempotency + triple-teleport gating + tilt threshold (`PaperHeadRendererHandle.tiltDiffers:86-88`) + yaw wrap at the 180/-180 seam (`CarrierMotion.java:72-76`) + the owner/generation identity check on `PaperModelEngineRenderer.java:89-90`'s existing-handle early return, mirroring `PaperHeadRenderer.java:46-48` so a stale handle from a previous generation cannot be reused after a reload.
8. Max health: the replacement landed in Phase 1. Here, confirm the `compatibility` job is green on all five points for the current HEAD, and smoke-check that the low-health trigger still edge-triggers and re-arms (Phase 9 repeats this live on both ends of the range).
9. Cleanups: listener unregister, SEVERE logging with throwable, LinkageError catch, dead code removal, permissions dedupe, and `PaperRuntimePetState.java:225-233` per-tick allocation of `RendererSpawnRequest`/`RuntimeTransform`/`Pose`/`MovementInput` — reuse scratch instances only if the change stays obviously safe; otherwise leave it and let Phase 9's spark numbers decide.
10. Full build; manual smoke on a local Paper server with MythicMobs+ModelEngine if available (else note for Phase 9).

## Todo

- [ ] Identity: plugin classloader + refresh + log + test
- [ ] hasTrigger gate on all handlers; projectile shooter unwrapped
- [ ] Bridge-vs-input quarantine split on 5 bridges; MythicLib modifier diffing
- [ ] Per-pet binding cap + reindex log-once + ThreadLocalRandom
- [ ] MethodHandle caching; typed overload match; pet-origin
- [ ] RateLimitedWarnings + self-heal retire
- [ ] Target resolver: entity-type + registry exclusion, sphere radius
- [ ] Same-world teleport relocation; kick/quit single-fire
- [ ] Max health: Phase 1 hotfix confirmed green on the full matrix; low-health trigger smoke-checked
- [ ] Render P3s (tilt threshold, yaw wrap at the 180 seam, collapse the duplicated retire bodies, owner/generation identity check on the ModelEngine existing-handle early return)
- [ ] Dead code removal + logging polish + permissions dedupe + per-tick alloc review (`PaperRuntimePetState`)
- [ ] Green: full build

## Success Criteria

- [ ] Unit test proves MythicMobs identity binds under a hostile classloader and recovers after refresh
- [ ] Sneak/jump/damage with no bindings submits zero tasks (asserted)
- [ ] One misconfigured pet/skill/buff never disables its bridge for others (three tests)
- [ ] Bow/trident hits fire `ON_ATTACK`/`ON_DAMAGE_TAKEN` (projectile shooter resolved)
- [ ] A third pet's skill bindings are not silently dropped (per-pet cap test)
- [ ] No `getMethod` call on cast/tick hot paths (grep-level check + review)

## Risk Assessment

- Quarantine split touches contract tests that read source text (37 known) — coordinate with Phase 7; update in the same commit as the behavior change, never weaken assertions.
- Teleport relocation may fight ModelEngine's own tracking. Signal: pets invisible/desynced after `/tp` in manual smoke. Response: fall back to despawn/respawn for ModelEngine handles only, keep relocation for head renderer.
- Supported range is 1.21.x-wide, so any Bukkit API used here must exist across that range — `compileCompatibilityJava` is the check that proves it, so run it locally before claiming a version fix works. A silent-wrong-value fallback (the `Registry.ATTRIBUTE` trap above) is worse than a compile error; prefer APIs that cannot degrade quietly.
- `paper-plugin.yml` is processed through Groovy `expand()` (`omnipet-paper/build.gradle.kts:44-48`), so introducing a literal `$` while deduping the permissions block breaks the build. Escape or avoid it.

## Security

- No new permissions or I/O. Removing per-event repository reads reduces DoS surface from high-rate movement events.
