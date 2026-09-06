---
phase: 7
title: "Modularization and test hardening"
status: pending
priority: P2
effort: "2d"
dependencies: [2, 3, 4, 5, 6]
---

# Phase 7: Modularization and test hardening

## Context Links

- Split lists in all four code scout reports (`plans/reports/from-scout-*-report.md`, ">200 lines" sections)

## Overview

Split the oversized files the scouts flagged, replace the fragile source-text contract tests in touched areas with behavioral assertions, and add the cross-cutting regression tests earlier phases could not host cleanly. Runs after the behavior phases so splits do not churn their diffs.

## Key Insights

- 37 contract tests read `.java` files as text (verified 2026-09-04 by grep for `Files.readString`/`Files.lines`/`Path.of("src/main`; 4 of them name `OmniPetPlugin.java` directly, an earlier draft said 6 — step 1's inventory settles it) — they pin mechanisms, not outcomes (the pet-follow bug shipped exactly this way, per CHANGELOG), and they break on any refactor including this phase's splits.
- **Decided (validation session 2, 2026-09-04): the full behavioural rewrite stays in scope.** The reduced form named in `plan.md`'s Review record is the overrun fallback only, not the default.

<!-- Updated: Validation Session 2 - full rewrite confirmed; OmniPetPlugin reader count corrected -->

- Priority splits (size + churn in this plan): `studio/bukkit/PetStudioController` (652 — largest Paper file, touched by Phases 5 and 6), `PetDefinitionStudioService` (607), `PaperActiveSkillController` (606), `OmniPetPlugin` (581), `command/OmniPetCommand` (576 — touched by Phase 5), `EggAdminController` (529), `PlayerHatchController` (405), `PaperPetRuntimeCoordinator` (382), `PaperModelEngineRenderer` (382), `PlayerPetController` (375), `PlacedEggCoordinator` (363), `PlayerSlotPurchaseController` (335), `SlotTransactionAdminController` (334), `RepositorySkillActionService` (299), `ReflectiveMythicLibBuffPort` (297), `SlotUnlockService` (297), `ReflectiveMythicMobsSkillProvider` (261), `BukkitPaperHeadRendererBackend` (263), `IdleBehaviour` (259), `YamlPetDefinitionRepository` (247 — also gets the single-scan `readAll()` in Phase 2), `PlayerStateYamlCodec` (224 — legacy v1-v3 decode into its own unit).
- Java files keep PascalCase; split boundaries follow the scouts' suggestions (e.g. `SkillTriggerIndex`, `OmniPetServiceWiring` + `OmniPetListenerRegistration`, studio draft-validation vs raw-node-generation vs transaction-holders).

## Requirements

- Functional: zero behavior change — splits are moves; every moved unit keeps its tests (moved or rewritten behavioral).
- Non-functional: no public API changes in core; paper-internal visibility tightened where the split allows. Test count is a reported metric, not a gate — this plan deletes tests on purpose.

## Architecture

Per file: extract the scouts' named seams into sibling classes in the same package; constructor-inject shared state; keep one orchestrating façade so wiring call-sites (`OmniPetPlugin`) change minimally. Contract tests touching moved code become behavior tests (fake backends asserting outcomes — e.g. "carrier position converges on owner" instead of "source contains setVelocity").

## Related Code Files

- Modify (split): the 21 files listed above under their existing packages; `OmniPetPlugin` → `OmniPetServiceWiring` + `OmniPetListenerRegistration`
- Modify: every `*ContractTest`/source-text test that reads a split file — rewrite behavioral or retarget; keep the ones guarding real invariants (permissions tree, catalog parity)
- Create: cross-cutting tests without a natural earlier home: same-world teleport relocation integration test, kick+quit double-fire idempotency, internal-reward replay after the items left the inventory (proves the persisted `INTERNAL_ATTEMPTING` marker, not the PDC scan, is what prevents a double reward), and an extension of Phase 3's no-lock-held assertion to the adapter-side `future.get` paths (using the holder introspection Phase 2 exposes) — planning confirmed the LuckPerms call runs in the `ENTITLEMENT_SYNC_PENDING` stage *outside* core's `withLocked`, so this guards against a regression rather than fixing a known bug

## Implementation Steps

1. Inventory the 37 source-text tests; classify: retarget (file renamed), rewrite behavioral (mechanism-pinning), keep (true source contracts like paper-plugin.yml shape).
2. Split files one at a time, largest churn first (`PetStudioController`, `OmniPetPlugin`, `PaperActiveSkillController`, `PlacedEggCoordinator`); full test run between splits.
3. Rewrite affected tests behaviorally in the same commit as each split.
4. Add the cross-cutting regression tests.
5. Verify no file this plan touched still exceeds ~200 lines without recorded justification (façades may).
6. Full build; report suite/test counts against the Phase 1 baseline (210 suites / 1116 tests) **with the delta explained** — deletions are deliberate (Phase 2 removes `EffectBudget` + 2 tests, Phase 5 removes `FoundationCommand`/`COMMAND_FOUNDATION_READY` coverage, this phase removes mechanism-pinning contract tests), so a count floor would be a false gate.

## Todo

- [ ] Source-text test inventory + classification
- [ ] Splits: PetStudioController, OmniPetPlugin, PaperActiveSkillController, PetDefinitionStudioService, OmniPetCommand, EggAdminController, PlacedEggCoordinator, PaperPetRuntimeCoordinator, PaperModelEngineRenderer + remaining list
- [ ] Behavioral rewrites of affected contract tests
- [ ] Cross-cutting tests (teleport, kick/quit, reward dedupe, lock assertions)
- [ ] Green: full build; test-count delta vs baseline reported and explained

## Success Criteria

- [ ] No mechanism-pinning source-text test remains on files this plan modified
- [ ] Every deleted test either names the behavioral test that replaced it, or the code it covered is gone
- [ ] Suite/test counts reported with the delta explained against 210 suites / 1116 tests (reported, not gated)
- [ ] All splits land with zero behavior diffs (test suite proves)
- [ ] Cross-cutting regressions covered

## Risk Assessment

- Splitting `OmniPetPlugin` collides with the license-gate plan's onEnable patches — that plan is blocked on this one (see plan.md); its Phase estimates must be re-checked against the new wiring classes when it starts.
- Big-bang splits risk long red periods. Mitigation: one file per commit, suite green between commits; abort a split (keep façade whole) if a seam turns out load-bearing.
- This phase is the plan's largest non-scout workload (the coverage audit flagged it as a test-quality project riding a bugfix release). If it overruns, the reduced form is: retarget only the tests the splits break, rewrite only the mechanism-pinning ones on files this plan already changed, and defer the remaining splits to a follow-up. Do not widen it beyond the list above.
