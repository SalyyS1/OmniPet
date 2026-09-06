---
phase: 2
title: "Core persistence and concurrency hardening"
status: pending
priority: P1
effort: "2.5d"
dependencies: [1]
---

# Phase 2: Core persistence and concurrency hardening

## Context Links

- Findings source: `plans/reports/from-scout-core-to-planner-core-defect-review-report.md` (all file:line refs there)

## Overview

Fix the omnipet-core P1 (shared SnakeYAML instances race under async loads and quarantine healthy player files) plus the eight core P2s: lock-registry hygiene, journal lock-map eviction, unbounded reads, stray-file bricking, journal scan cost, token-cap dead-end, stamina write churn, and at-least-once internal rewards.

## Key Insights

- `persistence/YamlDocuments.java:14-15` shares static `Yaml` READER/WRITER across threads; SnakeYAML `Yaml` is documented not thread-safe; Paper calls core from async tasks while main also loads. Decode failure quarantines the player file (`FilePlayerStateRepository.java:135-139`) — data-loss-shaped outcome from a transient race.
- `SharedRepositoryLockRegistry.java:52-65` spins forever on same-thread nested acquire (JVM lock is reentrant, file lock is not). Latent path: `SlotUnlockService.java:245` → `SlotPurchaseRecovery.completeIfEntitled` → `playerStates.snapshot` inside `withLocked`.
- Quarantine-on-any-RuntimeException in read paths (`FilePlayerStateRepository.java:135-140`) amplifies every transient fault into permanent player lockout.

## Requirements

- Functional: all fixes below preserve on-disk formats and public core APIs (schema versions unchanged).
- Non-functional: no new dependencies; every fix lands with a regression test.

## Architecture

- `ThreadLocal<Yaml>` inside `YamlDocuments` (reader + writer); no API change.
- Lock registry: track owner thread per entry; same-thread nested acquire fails fast with `IllegalStateException`; file-lock wait bounded (~30s) then `IOException`. `completeIfEntitled` takes current state as a parameter instead of re-reading under lock.
- New `BoundedFiles.readString(path, maxBytes)` helper in `persistence`, used by `FilePlayerStateRepository` (4 MiB), `YamlPetDefinitionRepository` (1 MiB), and existing journals (replacing their per-journal 16 KiB checks where straightforward).
- Directory-scan hygiene: skip-and-report invalid basenames via problems sink (pattern from `SkillBindingProjection.read`) in `YamlPetDefinitionFiles.scan`, `CultivationItemActionFileJournal.scan`, `PurchaseJournalScanner` (null-filter before sort).
- Journal lifecycle: archive terminal egg-escrow records to a `done/` subdir on transition so `findByPlayer` join scans stay O(active); keep the 10k bound as a truncation warning, not a throw.

## Related Code Files

- Modify: `omnipet-core/src/main/java/io/github/salyvn/omnipet/core/persistence/YamlDocuments.java`, `SharedRepositoryLockRegistry.java`, `FilePlayerStateRepository.java`, `YamlPetDefinitionFiles.java`, `YamlPetDefinitionRepository.java`
- Modify: `omnipet-core/.../economy/FilePurchaseJournal.java` (reuse `EggEscrowLockRegistry`-style eviction), `SlotUnlockService.java` + `SlotPurchaseRecovery.java` (pass state through), `PurchaseJournalScanner.java`
- Modify: `omnipet-core/.../incubation/FileEggEscrowJournal.java` (archival + bounded scan), `HatchService.java` (exempt cancel/complete from 128-token cap)
- Modify: `omnipet-core/.../progression/RepositoryProgressionService.java` + `ProgressionService.java` (regen no-op at cap), `release/ReleaseIdentity.java` (exclude stamina fields from `petFingerprint`), `CultivationItemActionFileJournal.java`
- Modify: `omnipet-core/.../release/ReleaseOutboxDeliveryService.java` (persist `INTERNAL_ATTEMPTING` before delivering; convert `recoverExternal` recursion to loop), `ReleaseOutboxStore.java` (decode-validating `isUsable`, corrupt entries → `OUTBOX_INVALID` statuses)
- Modify (P3 batch): `DeterministicHatchRollService.java` (sort candidates/bands by id, bump `ALGORITHM_ID` to `splitmix64-v2`), `ProgressionService.java` (saturate EXP, bound formula cache), `SkillBindingProjection.java` (stable default bindingId from `provider:skillId`), `SkillBinding.java` (cap cooldown/interval ≤30d), `RawNodeValues.java` (reject non scalar/Map/List), `EffectBudget.java` (delete — zero production references) **but not both of its tests**: `RuntimeFleetBudgetEvidenceTest` is the deterministic 100-pet operation-count evidence README and Phase 9 cite, so it stays — replace its `EffectBudget` field with an inline counter; only the `EffectBudget` case in `RuntimePortsTest:55` is removed, `MovementController.java`/`IdleBehaviour.java` (scratch-vector allocs only if trivially safe), `FilePurchaseJournal.create` (add the `sameIdentity` check its sibling journals have), `YamlPetDefinitionRepository` (single-scan `readAll()`, cache `scan()` per operation, evict the `locks` map), `AtomicFileStore` (parent-directory fsync after `move` on non-Windows)
- Modify: `omnipet-core/.../core/domain/incubation/IncubationState.java` is where `MAX_ACTION_TOKENS = 128` lives (`:19`), not `incubation/` — the cap check itself stays in `HatchService.duplicate`.

<!-- Updated: Validation Session 2 - IncubationState path corrected; EffectBudget deletion keeps RuntimeFleetBudgetEvidenceTest; read bound is a constant -->
- Constraint from the contract check: `new FilePlayerStateRepository(` has 38 call sites (mostly tests). `BoundedFiles` limits are constants on each repository, never constructor parameters.
- Create: `omnipet-core/src/main/java/io/github/salyvn/omnipet/core/persistence/BoundedFiles.java`

## Implementation Steps

1. `YamlDocuments` → `ThreadLocal<Yaml>`; add parallel-load stress test (N threads loading the same and different player files; assert no quarantine, stable decode).
2. Lock registry owner-thread tracking + bounded wait + nested-acquire failure; also expose holder/owner introspection so tests elsewhere can assert "no lock is held here" (Phase 3 step 4 and Phase 7 both need it). Test: nested acquire throws instead of hanging; concurrent acquire across threads still serializes.
2b. Evict per-player lock entries in `FilePurchaseJournal` and `CultivationItemActionFileJournal` — move both onto the refcounted, evicting `EggEscrowLockRegistry` (or its shape) so neither map grows for the server's lifetime; test that an entry disappears once its last holder releases.
3. `BoundedFiles` + adoption in the two repos and journals; test oversize file → clean error, not OOM/quarantine.
3b. Narrow the read-path quarantine policy: quarantine only on codec-classified decode failure; any other `RuntimeException` propagates. The ThreadLocal fix removes the known trigger but the policy is what turns a transient fault into a permanent player lockout. Test: an injected transient `IOException`/`RuntimeException` during `snapshot` does not quarantine the file.
4. Scan hygiene (skip-and-report) in the three scanners; test stray `my pet.yml` no longer bricks `list()`; `PurchaseJournalScanner` null-filter.
5. Escrow journal `done/` archival on terminal transition; `findByPlayer` skips archive; migration: first scan moves existing terminal records. Apply the same archival to the purchase and cultivation-action journals so no per-join or per-admin scan grows for the server's lifetime, and give `CultivationItemActionFileJournal.scan` a `MAX_SCAN_FILES` bound plus the same lock model as its siblings (it uses bare `synchronized` today).
6. `HatchService` cancel/complete exempt from token cap; test at 128 tokens cancel still succeeds.
7. Stamina regen no-op at cap; `petFingerprint` excludes stamina/`lastStaminaEpochMillis`; tests for both (release preview stays valid across a regen).
8. Outbox: `INTERNAL_ATTEMPTING` persisted pre-delivery (required — the Paper gateway's PDC dedupe only holds while the player still carries the items; see Phase 3's verification note); corrupt outbox entries surface as statuses not exceptions (including the `:184-188` `longValue()` truncation of an oversized epoch-millis field, which today silently mangles the timestamp instead of reporting the entry as invalid); recursion → loop.
9. P3 batch (hatch-roll ordering + algorithm bump, EXP saturation, bindingId, cooldown caps, RawNodeValues, delete EffectBudget, `FilePurchaseJournal.create` identity check, definition-repo single-scan + lock eviction, `AtomicFileStore` parent fsync) each with focused test updates.
10. Write the migration contract, since this phase creates most of it: enumerate every one-way on-disk change this release makes (escrow terminal records moved to `done/`, `ALGORITHM_ID` → `splitmix64-v2`, the new `INTERNAL_ATTEMPTING` outbox state, purchase/cultivation journal archival, plus Phase 3's planned legacy egg-nonce inventory rewrite and Phase 6's new config keys), emit exactly one log line per migration as it runs so an operator can see it happened, and state the rollback position plainly: back up `plugins/OmniPet/` before the first 3.0.0 boot; after that boot there is no supported downgrade to 2.x except restoring that backup. The Phase 3 and Phase 6 entries are forward-looking at this point — Phase 8 reconciles the list against what those phases actually shipped before publishing it as `docs/migration.md`.
11. Full core test run; then full build.

## Todo

- [ ] ThreadLocal Yaml + parallel-load test
- [ ] Lock registry: owner thread, bounded wait, nested-acquire error, holder introspection for tests + tests
- [ ] Journal lock maps refcounted + evicting (purchase, cultivation)
- [ ] BoundedFiles helper adopted by repos/journals
- [ ] Read-path quarantine narrowed to codec-classified failures + test
- [ ] Skip-and-report stray files in 3 scanners
- [ ] Terminal-record archival for escrow, purchase, cultivation-action journals; cultivation scan bounded
- [ ] Token-cap exemption for cancel/complete
- [ ] Stamina no-op at cap + fingerprint exclusion
- [ ] INTERNAL_ATTEMPTING before internal delivery; corrupt-outbox statuses
- [ ] P3 batch: hatch sort + splitmix64-v2, EXP saturate, stable bindingId, cooldown cap, RawNodeValues guard, delete EffectBudget (fleet-budget evidence test kept, counter inlined), purchase-journal identity check, definition-repo single scan + lock eviction, AtomicFileStore parent fsync
- [ ] Migration contract written: one-way changes enumerated, one log line each, backup + no-downgrade statement
- [ ] Green: `gradlew :omnipet-core:test` then full build

## Success Criteria

- [ ] Parallel-load test proves no quarantine under concurrent YAML load
- [ ] A transient (non-decode) failure during a read never quarantines a player file
- [ ] Nested same-player lock acquire fails fast (test), never spins
- [ ] Stray file in definitions dir degrades to a warning, registry stays readable
- [ ] Release preview survives stamina regen (fingerprint test)
- [ ] An internal reward is marked `INTERNAL_ATTEMPTING` on disk before it is delivered (Phases 3 and 7 both depend on this)
- [ ] Every one-way migration logs once and is listed in the migration contract
- [ ] All core tests green; no schema version changed

## Risk Assessment

- Hatch-roll sort changes outputs for existing seeds → that is why `ALGORITHM_ID` bumps to `splitmix64-v2`; old records keep old ID. Signal: determinism test comparing recorded fixtures fails → keep old path keyed by algorithm ID, never rewrite stored results.
- Escrow archival touches join-time recovery — keep `transition` idempotency test green; archive move must be atomic (`AtomicFileStore` pattern). Verified compatible: `FileEggEscrowJournal.java:85-91` scans with `Files.newDirectoryStream(root, "*.yml")` plus a regular-file filter, so a `done/` subdirectory is skipped with no scan change.
- Legacy stack nonce split (Phase 3) and the archival moves here are irreversible. The migration contract in step 10 is the mitigation; if it is not written, the release ships with no rollback story.
- Stamina fingerprint exclusion needs **no** record-version gate. Traced during review: `ReleaseService.java:88,120` compares a freshly computed fingerprint against one carried by the in-session `ReleasePreview`, and `ReleaseOutboxStateUpdater.sameIdentity:112-118` compares two persisted entries with each other; grepping `petFingerprint|confirmationToken` across `omnipet-paper/src/main/java` returns nothing, so no preview or token is persisted. Worst case across the upgrade is a preview issued before a restart needing re-issue, which already dies with the session.

## Security

- Bounded reads close a disk-DoS vector (multi-MB YAML). No new secrets, no new I/O surfaces.
