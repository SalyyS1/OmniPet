---
title: "Release readiness: bugfix sweep, upgrades, wiki polish"
description: "Fix all 126 findings from the 5-scout audit (9 P1/High, 40 P2/Med, 77 P3/Low), ship operator upgrades, truth-up wiki/docs, certify, and cut OmniPet 3.0.0"
status: in-progress
priority: P1
effort: "19d"
branch: main
tags: [bugfix, refactor, docs, infra, critical, release]
blockedBy: []
blocks: [260806-2047-license-and-ip-control]
created: 2026-09-03
---

# Release readiness: bugfix sweep, upgrades, wiki polish

## Overview

Verified baseline: build green, 210 suites / 1116 tests (core 369, paper 747), artifact `build/release/OmniPet-3.0.0-SNAPSHOT.jar`; 49 files uncommitted (idle-play feature, builds green). Five read-only scout audits (core, sagas, runtime, ux, wiki) produced a 126-finding defect inventory in `plans/reports/from-scout-*-report.md`: 6 code P1s, 3 wiki Highs, 40 P2/Med, 77 P3/Low. This plan fixes all of it, adds release-blocking operator features, aligns wiki/docs with shipped behavior, and certifies 3.0.0.

**Refreshed 2026-09-04** against a re-run baseline (still 210 suites / 1116 tests, 0 failures; JAR now 2,014,223 bytes, 1062 classes) and live CI state. Two facts changed the plan: the `compatibility` job has been **red on `main` since 2026-08-06** (4 of 5 matrix points fail to compile), and the docs site is one commit stale because its last deploy timed out. Details in the refresh record below.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | Fix all 6 P1 defects (YAML thread-safety, egg dup/griefing, ready-egg dead-end, tick freeze, dead MythicMobs kill identity, trigger fan-out) | P1 |
| 2 | Fix all P2s and the P3 backlog: the two separate quarantine problems (core read-path quarantine on transient faults, and the vendor-bridge bad-input-vs-broken-ABI split), lock hygiene, scan bounds, protection-plugin safety, i18n catalog | P1 |
| 3 | Operator upgrades: `/pet version`, `/pet admin diagnose`, offline stats, configurable studio timeout, bStats metrics + update checker (on by default, config opt-out) | P2 |
| 4 | Wiki: fix the 2 renderer defects and the stale-claim cluster, close command/config coverage gaps, a11y + CI stats pipeline | P1 |
| 5 | Certification gate (spark numbers, vendor-absent boot, durability drills) then cut and tag 3.0.0 | P1 |

## Dependency Map

```
1 Baseline
├── 2 Core hardening ──► 3 Saga timing ──┐
├── 4 Runtime/render/skill ──────┬───────┤
└── 5 Command/GUI/config/i18n ───┴► 6 Operator upgrades (needs 4 and 5)
                                                        │
        (2, 3, 4, 5, 6 all complete) ──► 7 Modularization + tests ──► 8 Wiki/docs ──► 9 Certification ──► 10 Release cut
```

Phases 2+3, 4, and 5 are mostly independent tracks after Phase 1 (packages `core/persistence`+`core/economy` / `paper/runtime`+`render`+`skill` / `paper/command`+`gui`+`config`), but they are **not fully file-disjoint**: `OmniPetPlugin.java` is needed by Phase 3 (`hatchPlacedEgg` failure reporting, `invalidateDefinitions()`) and Phase 4 (SEVERE logging, `LinkageError` catch, Javadoc prune), and `paper-plugin.yml` by Phase 4 (dedupe permissions) and Phase 5 (remove `omnipet.admin.explore`). If the tracks are staffed in parallel, **Phase 4 owns both files** and Phases 3 and 5 hand over their one-line needs; if run serially, order does not matter. Phase 3 needs Phase 2's bounded lock registry. Phase 1 now also carries the one-line max-health hotfix that turns the `compatibility` job green (see the refresh record), so Phase 4 inherits a green matrix instead of diagnosing a red one. Phase 6 needs Phase 4's bridge health surface and Phase 5's command-tree derivation. Phase 7 must follow every behavior phase (it splits their files). Phase 8 needs Phase 3's egg semantics, Phase 4's Paper-range outcome, Phase 5's tree truth, Phase 6's new commands, and Phase 7's splits (it documents the shipped surface, so it runs after the code settles). Phases 9 and 10 are strictly last.

Serial effort is ~19d. If the three tracks are staffed in parallel the critical path is ~12d (1 → 2 → 3 → 7 → 8 → 9 → 10), because Phase 7 cannot start until every behavior phase has landed.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Baseline and settle in-progress work](./phase-01-start.md) | Pending |
| 2 | [Core persistence and concurrency hardening](./phase-02-core-persistence-and-concurrency-hardening.md) | Pending |
| 3 | [Saga timing and main-thread safety](./phase-03-saga-timing-and-main-thread-safety.md) | Pending |
| 4 | [Runtime, render, skill integration fixes](./phase-04-runtime-render-skill-integration-fixes.md) | Pending |
| 5 | [Command, GUI, config, i18n fixes](./phase-05-command-gui-config-i18n-fixes.md) | Pending |
| 6 | [Operator upgrades and diagnostics](./phase-06-operator-upgrades-and-diagnostics.md) | Pending |
| 7 | [Modularization and test hardening](./phase-07-modularization-and-test-hardening.md) | Pending |
| 8 | [Wiki polish and docs truth-up](./phase-08-wiki-polish-and-docs-truth-up.md) | Pending |
| 9 | [Certification gate](./phase-09-certification-gate.md) | Pending |
| 10 | [Release cut](./phase-10-release-cut.md) | Pending |

## Decisions (binding, from validation interview 2026-09-03)

| Decision | Choice | Affects |
|---|---|---|
| Supported Paper range | 1.21.x broadly (1.21 → current). No paper-api bump; read max health via `player.getMaxHealth()` rather than the renamed `Attribute` constant or a `Registry` lookup that returns null on the floor. Verified 2026-09-04 by `javap` on the 1.21.11 API jar: `Damageable.getMaxHealth()` present, `GENERIC_MAX_HEALTH` absent | Phase 1 (hotfix), Phase 4 (verify) |
| Ready placed-egg semantics | Auto-hatch retry pass + CLAIM action in the placed-egg menu + breaking a READY egg grants the pet (not the raw egg) | Phase 3 |
| License | All Rights Reserved (proprietary text, no redistribution rights) | Phase 10, and the pending license-gate plan |
| Metrics / update checker | Both on by default, both disableable in `config.yml` | Phase 6 |
| Repo visibility | Stays **public** and ships the All Rights Reserved LICENSE (source visible, no reuse or redistribution rights) — confirmed 2026-09-04 | Phase 10 |
| Compile matrix | Keep the full `compatibility` matrix (Paper 1.21 → 26.2, Java 25 toolchain for the 26.x points, CI-provisioned). Docs say "compiles against"; the certified range comes only from Phase 9 evidence — confirmed 2026-09-04 | Phases 1, 8, 9 |
| Phase 9 vendors | ModelEngine and the premium MythicMobs builds will be available, so Tier 2 evidence runs for real; the waiver is a fallback only — confirmed 2026-09-04 | Phase 9 |

## Review record

Two advisory passes ran against this plan before implementation:

- `plans/reports/from-coverage-audit-to-planner-scout-findings-coverage-matrix.md` — mechanical trace of all 126 scout findings against the phases. Result at time of writing: 105 covered, 10 partial, 11 orphan (every P1/High covered; all orphans P3/Low). **All 21 partials and orphans have since been folded into the phase files**, along with the four missing split candidates, the six sub-item gaps, and the cheap upgrade bullets. Re-read that report only for the audit trail, not as an open worklist.
- `plans/reports/from-red-team-to-planner-release-plan-red-team-report.md` — adversarial review. Verdict at time of writing: **NEEDS REVISION**, 6 Critical / 9 Major / 6 Minor / 11 Verified-OK. **All Critical and Major items have been applied to the phase files**, including three the plan had factually wrong: bStats cannot be added as a dependency (no Shadow plugin; the fat JAR is a hand-rolled `zipTree` merge, so it would ship unrelocated) and is now vendored; releasing the player lock around the provider call would have written a stale pre-withdrawal snapshot after the money left the player, so Phase 3 now re-reads and re-validates on re-lock; and the max-health "P1" was contested by the repo's own `compileCompatibilityJava` matrix, so Phase 1 was told to record that job's status before Phase 4 picked a fix (since settled on 2026-09-04: the job is red, the P1 stands, the hotfix moved into Phase 1 — see the refresh record). It also killed two items: Phase 2 needs no fingerprint record-version gate (nothing compares a persisted fingerprint against a fresh one), and `from-code-reviewer-to-orchestrator-post-plan-defect-review-report.md` is fully closed in current code — do not re-open its five findings.

Two items the coverage audit flagged as **scope beyond defect repair** — kept deliberately, named here so they are not mistaken for bug fixes:

- Phase 7's rewrite of the 37 source-text contract tests is a test-quality project riding a bugfix release. It is kept because Phases 4-7 break those tests anyway; if Phase 7 overruns, the reduced form is "retarget the tests the splits break, rewrite only the mechanism-pinning ones on files this plan changed" — do not widen it.
- Phase 1 commits the 49-file idle-play feature, which no scout reviewed. It ships inside a "fix everything the audit found" release. Accepted because the tree must be clean to work the phases; treat any idle-play defect found later as a normal post-release fix, not a gate on 3.0.0.

## Refresh record (2026-09-04)

Second-day pass with a working shell (yesterday's session had none). Nothing in the five scout reports was re-scouted — no commit has landed since — but every P1 was re-confirmed at its cited line, and the previously unreviewed idle-play working tree was read in full. Corrections applied to the phase files:

- **`compatibility` CI is red, and has been since 2026-08-06.** Run 31252250448 (HEAD `fc22e2c`): the `gradle` job and `Paper 1.21-R0.1-SNAPSHOT / Java 21` are green; the other four matrix points (`1.21.11-R0.1-SNAPSHOT`, `26.1.1`, `26.1.2`, `26.2`) fail with `SkillTriggerListener.java:197: error: cannot find symbol GENERIC_MAX_HEALTH`. Reproduced locally against `1.21.11-R0.1-SNAPSHOT` (`build/compat-1.21.11-260904.log`). `javap` on that API jar: `Attribute` is an interface exposing `MAX_HEALTH` only; `Damageable.getMaxHealth()` is present. So the max-health item is a genuine P1 — a JAR built today throws `NoSuchFieldError` on the first damage event for any owner with a low-health binding on a 1.21.11 server — not a deprecation cleanup, and a red required check on `main` is a release blocker in its own right. The one-line fix moves to Phase 1 as a hotfix; Phase 4 keeps the verification and Phase 9 the drift guard.
- **Dependency locking is live, not inert.** `omnipet-core/gradle.lockfile`, `omnipet-paper/gradle.lockfile`, and `settings-gradle.lockfile` exist and are tracked. Open question 5 and Phase 1 step 7 were wrong; corrected. Decision: keep locking. Phase 6 adds no dependency (bStats is vendored), so no `--write-locks` run is expected this release.
- **Docs site is stale.** The Pages workflow last deployed `14bb349`; the run for `9bf8629` timed out after ten minutes in `deployment_queued` (GitHub-side queue — every verify step had passed). Live `wiki-stats.json` says 1016 tests / 199 suites, the committed file says 1087 / 207, the build says 1116 / 210. Phase 1's docs commit re-triggers the deploy; Phase 8 adds a `timeout-minutes` guard alongside the CI-owned stats pipeline it already planned.
- **Idle-play working tree reviewed (49 files, none scout-reviewed before).** No blocking defect. Three notes handed on: `idle-play.enabled` defaults to `true`, so upgrading servers gain world-visible heart particles every 10 ticks per playing pet (bounded: count ≤ 16, cadence ≥ 5 ticks) — Phase 9's all-switches-off run must include `idle-play.enabled: false`; a particle-backend failure is reported through the same un-deduplicated runtime failure sink, under the `MOVEMENT` stage, so Phase 4's `RateLimitedWarnings` must cover it; `advanceIdle` now runs before the movement step, deliberately, because the tick's idle state chooses the target.
- **Repository is public with no LICENSE and no tags** (`gh repo view`: `visibility: PUBLIC`, `licenseInfo: null`). Phase 10's "All Rights Reserved on a public repo" risk is live today, not hypothetical; it is now open question 6 and gates the tag push.
- **No bStats service ID exists anywhere in the repo.** Phase 6 ships the vendored class with the ID as a blank config value (no-op) unless the operator supplies one.
- Baseline re-verified: `gradlew clean build` green — core 75 suites / 369 tests, paper 135 suites / 747 tests, 0 failures / errors / skips; `build/release/OmniPet-3.0.0-SNAPSHOT.jar` 2,014,223 bytes, SHA-256 `4ffa336a4a02eb74c6977c3156459e695fd834ccb895ac5c6ef0be1545d4d2e7`, 1161 entries, 1062 classes. README still quotes the 2026-08-03 checkpoint (1,727,430 bytes / 959 classes) — on Phase 8's stale-claim list.
- Working tree today: 55 dirty paths (the 49 idle-play files plus this plan's own directory, reports, and journal). `.claude-terminal` stays out of every commit.

## Cross-Plan Dependencies

- `260806-2047-license-and-ip-control` (awaiting approval) is **blocked by this plan**: it patches `OmniPetPlugin.onEnable`, relies on source-text contract tests this plan replaces (Phase 7), and obfuscation interacts with the reflective bridges Phase 4 rewrites. Do not start it mid-sweep.
- `260805-0249-performance-gameplay-wiki-roadmap`: its wiki-UX Phase 13-14 and certification Phase 15 are absorbed here (Phases 8-9). Its gameplay expansion (Phases 6-12: food/bond, temp buffs, party/formation, GUI polish) stays open as post-release work, not blocked by this plan.

## Open questions

Status after the 2026-09-04 refresh and the operator's answers the same day. Only question 3 remains open, and it does not block any phase.

| # | Question | Status |
|---|---|---|
| 1 | Is the `compatibility` CI job green? | **Answered: red** since 2026-08-06 on 4 of 5 matrix points (`GENERIC_MAX_HEALTH`). Hotfix moved into Phase 1. |
| 2 | Keep asserting Paper `26.x` / Java 25 compile support while documenting "supported: 1.21.x"? | **Answered (operator, 2026-09-04): keep the full matrix.** Docs state two facts — "compiles against 1.21 → 26.2" and the Phase 9 certified range. No local Java 25 toolchain needed; CI provisions its own. |
| 3 | Is a bStats service ID registered for OmniPet? | **Still open.** None in the repo. Phase 6 ships the ID as a blank config value (no-op) unless one is supplied before that phase. |
| 4 | Will Phase 9 have ModelEngine and premium MythicMobs builds? | **Answered (operator, 2026-09-04): yes, both.** Tier 2 runs for real; the waiver wording stays as fallback only. |
| 5 | Is `dependencyLocking` inert? | **Answered: no.** Three tracked lockfiles exist; locking is active. Keep it. |
| 6 | The repo is public with no LICENSE — is All Rights Reserved on a public repo intended? | **Answered (operator, 2026-09-04): yes — stay public, ship ARR.** Phase 10's tag push is no longer gated. |

## Deferred (post-release, not defects)

- A single `MainThreadBridge` helper replacing the three copies of the sync-hop idiom.
- Reducing `docs/*.md` duplication against the wiki, plus a CI diff-check between them. Phase 8's generated `wiki-commands.json` drift check already covers the commands surface.
- The gameplay expansion in `260805-0249` (food/bond, temporary buffs, party/formation, GUI polish).

## Success Criteria

- [ ] All P1/P2 findings from the five scout reports fixed or explicitly waived with reason; P3s fixed or ticketed
- [ ] Full build green with new regression tests (parallel YAML load, classloader bind, lock timeout, protection-cancel, ready-egg retry)
- [ ] Wiki renders commands/config truthfully; zero stale claims in README/docs; verify scripts extended and green
- [ ] Certification evidence recorded (spark at 10/50/100 pets, vendor-absent boot, kill -9 durability, no egg dup)
- [ ] `3.0.0` tagged, CHANGELOG finalized, release artifact published

## Validation Log

Session 1 (2026-09-03) was recorded as the "Decisions (binding, …)" table above rather than as a log; its four answers are unchanged.

### Session 2 — 2026-09-04
**Trigger:** day-two refresh of the plan with a working shell; operator chose `/ak:plan validate` at the handoff.
**Questions asked:** 8 (four during the refresh handoff, four in the validate interview)

#### Verification Results
- **Tier:** Full (10 phases; Fact Checker on every phase, Flow Tracer ×2, Scope Auditor ×1, Contract Verifier ×8)
- **Claims checked:** 91
- **Verified:** 81 | **Failed:** 6 | **Unverified:** 4

##### Failures (all corrected in the owning phase file this session; each is a path or line that moved, not a wrong finding)
1. [Fact Checker] Phase 2 — `incubation/IncubationState.java` → actual `omnipet-core/.../core/domain/incubation/IncubationState.java:19` (`MAX_ACTION_TOKENS = 128`).
2. [Fact Checker] Phase 2 — "delete `EffectBudget` + 2 tests (dead)": `EffectBudget` has zero production references (dead, confirmed), but one of the two tests is `RuntimeFleetBudgetEvidenceTest.oneCentralPassProcessesOneHundredPetsWithDeterministicBoundedWork`, which is the "deterministic 100-pet operation counts" evidence README and Phase 9 cite. Deleting it would erase the baseline Phase 9 compares against. Corrected: delete `EffectBudget`, rewrite that test's counter inline, drop only the `RuntimePortsTest` case.
3. [Fact Checker] Phase 4 — tilt threshold cited at `BukkitPaperHeadRendererBackend.java:203-219`; the `0.02` speed-delta test is `PaperHeadRendererHandle.tiltDiffers:86-88`, and the backend's `setTransformation` call it triggers is at `:212`.
4. [Fact Checker] Phase 4 — "`PaperPetRuntimeCoordinator.java:168-173` teleport": that method is `ownerWorldChanged` (`:168`), reached from `PlayerStorageLifecycleListener.onTeleport:72-75` for every teleport, same-world included — the finding holds, the name was wrong.
5. [Fact Checker] Phase 5 — `gui/GuiConfigLoader.java` → actual `config/GuiConfigLoader.java` (`Number` truncation at `:196` and `:208`).
6. [Fact Checker] Phase 5 — `management/PetManagementMenuRenderer.java:183-228` → actual `gui/player/PetManagementMenuRenderer.java:205-217` (`MenuButtonStyle.slot`/`.material` resolution duplicated from `PlayerPetMenuRenderer`).

##### Unverified
- Phase 4 — `ReflectiveMythicMobsSkillProvider.java:110-121` per-cast reflection: `findTargetedCast` is invoked at `:113`, but the `getMethods()` scans live at `:198` and `:213`; behaviour as described, lines partly off.
- Phase 5 — `MenuLayout` "accepts out-of-range slots silently": partially true. `:137-139` warns and falls back to the default slot; `:141` silently `continue`s; negative slots are unchecked on both paths. Phase 5 step 1 stands, targeting the silent path and the negative case.
- Phase 5 — `Nameplate.java:59,89` MiniMessage parse lines: the parse is confirmed by the class Javadoc (`:64-75`, "parts spliced in as raw MiniMessage and the whole line parsed once") but the cited line numbers no longer land on the calls.
- Phase 7 — "6 contract tests read `OmniPetPlugin.java`": grep finds 4 test files naming it; the 37 source-text-test total is verified. Phase 7 step 1's inventory settles the exact count.

##### Contract / flow / scope spot-checks (all verified)
- `YamlDocuments.readMap/writeMap`: 25 call sites — the `ThreadLocal` change is internal, none change.
- `new FilePlayerStateRepository(`: 38 call sites (mostly tests) — Phase 2's read bound must be a constant, not a constructor parameter, or all 38 churn.
- `SlotUnlockService`: 7 files; `recoverInternal`: 7 callers; `hasTrigger`: exactly the 3 gated handlers the plan names (drop, swap, interval); `MenuLayout`: 5 consumers; `PaperRuntimeBootstrap.create`: 2 callers across the 3- and 4-arg overloads; `new OmniPetConfig(`: 1 production call site.
- Flow: `PlacedEggCoordinator.pass()` never invokes `onReady` (only `PlacedEggView:219` on the exact tick transition) — Phase 3's ready-egg dead-end confirmed.
- Flow: `MythicMobsSkillLifecycleListener` refreshes the skill context (`:67,76,138`) and `OmniPetPlugin:178` refreshes providers; nothing calls the kill-identity `refresh()` — Phase 4's P1 confirmed.
- Scope: the trigger index is a `ConcurrentHashMap<UUID, OwnerTriggers>` on the process-global controller, keyed per owner, refreshed by `refreshTriggers(ownerId)` — Phase 4's "allocation-free per-owner index" is accurate.

#### Questions & Answers

1. **[Scope]** The plan is refreshed and validated. What next?
   - Options: /ak:cook the plan (Recommended) | /ak:plan validate again | End session
   - **Answer:** /ak:plan validate again
   - **Rationale:** triggered this verification pass and interview.
2. **[Risks]** The GitHub repo is PUBLIC with no LICENSE. Yesterday's decision was All Rights Reserved. Which do you want before the v3.0.0 tag is pushed?
   - Options: Keep public + All Rights Reserved | Make the repo private first | Switch to an open-source license
   - **Answer:** Keep public + All Rights Reserved
   - **Rationale:** closes open question 6; Phase 10's tag push is ungated.
3. **[Tradeoffs]** CI compiles against Paper 1.21 → 26.2 (Java 25) but the supported range is documented as 1.21.x. Keep the wider matrix?
   - Options: Keep the full matrix (Recommended) | Trim to 1.21.x only
   - **Answer:** Keep the full matrix
   - **Rationale:** Phase 8 documents "compiles against" and "certified" as two facts; Phase 9 needs no local Java 25.
4. **[Assumptions]** Will Phase 9 (live certification) have ModelEngine and the premium MythicMobs builds available?
   - Options: Yes, both available | No, use the Tier-2 waiver | Only one of them
   - **Answer:** Yes, both available
   - **Rationale:** Tier 2 evidence is expected for real; the waiver stays as fallback only.
5. **[Architecture]** Phase 5 derives command permissions from `OmniPetCommandTree`. Where the two hardcoded permission lists are looser than the tree today, which side wins?
   - Options: Tree wins, tighten (Recommended) | Loosen the tree to match | Decide per node in Phase 5
   - **Answer:** Tree wins, tighten
   - **Rationale:** looser entry-point checks are drift bugs; every tightened node is disclosed in the 3.0.0 release notes.
6. **[Architecture]** Phase 8 must pick one CI shape for the wiki stats file. Which?
   - Options: workflow_run handoff (Recommended) | Build commits the file | Generate inside Pages job
   - **Answer:** workflow_run handoff
   - **Rationale:** no bot commits on `main`, no Java in the Pages job; Pages downloads the Build artifact by run id.
7. **[Assumptions]** Phase 6 update checker needs a default endpoint. Which?
   - Options: GitHub Releases API (Recommended) | Modrinth or Hangar | Custom URL
   - **Answer:** GitHub Releases API
   - **Rationale:** public repo, no token; fails closed until `v3.0.0` exists.
8. **[Scope]** Phase 7 rewrites the ~37 source-text contract tests behaviourally. The coverage audit flagged this as beyond defect repair. Keep it?
   - Options: Full rewrite (Recommended) | Reduced form | Retarget only, no rewrites
   - **Answer:** Full rewrite
   - **Rationale:** the reduced form in the Review record stays documented as the overrun fallback only.

#### Confirmed Decisions
- Repo visibility: public + All Rights Reserved — operator accepts source-visible, no-reuse.
- Compile matrix: full (1.21 → 26.2) — documented as "compiles against", never "supported".
- Phase 9 vendors: both available — Tier 2 runs for real.
- Permission derivation: tree wins — tightened nodes listed in release notes.
- Wiki stats CI: `workflow_run` handoff from Build to Pages.
- Update-check endpoint: `https://api.github.com/repos/SalyyS1/OmniPet/releases/latest`, overridable in `config.yml`.
- Phase 7 scope: full behavioural rewrite of the 37 source-text tests.

#### Action Items
- [x] Six path/line failures corrected in Phases 2, 4, 5 (this session)
- [x] `RuntimeFleetBudgetEvidenceTest` preserved through the `EffectBudget` deletion (Phase 2 step 9 rewritten)
- [x] Decisions 5-8 propagated to Phases 5, 6, 7, 8
- [ ] Phase 1: everything in its Todo — the first cook step

#### Impact on Phases
- Phase 2: `IncubationState` path fixed; `EffectBudget` deletion now keeps the fleet-budget evidence test; read bound must be a constant (38 constructor call sites).
- Phase 4: tilt-threshold and teleport-handler references corrected; skill-provider line refs widened.
- Phase 5: `GuiConfigLoader` and `PetManagementMenuRenderer` paths fixed; `MenuLayout` finding narrowed to the silent path + negatives; "tree wins" recorded with the release-notes obligation.
- Phase 6: update-check endpoint fixed to the GitHub Releases API.
- Phase 7: full rewrite confirmed; `OmniPetPlugin` reader count left to the step-1 inventory.
- Phase 8: stats pipeline shape fixed to `workflow_run`.
- Phase 10: release notes must enumerate tightened permission nodes (already required; now backed by decision 5).

### Whole-Plan Consistency Sweep
- Files reread: plan.md, phase-01-start.md, phase-02 … phase-10 (all ten)
- Decision deltas checked: 14 (six path/line corrections; the `EffectBudget` test carve-out; lockfile status; compat-job status; repo visibility; compile matrix; Phase 9 vendors; permissions; stats CI shape; update endpoint; Phase 7 scope)
- Reconciled stale references: 6 path/line fixes across Phases 2, 4, 5; 4 decision propagations across Phases 5, 6, 7, 8; the "pick one shape" fork in Phase 8 and the "needs operator" rows in the open-questions table replaced with the recorded answers
- Remaining mentions of superseded terms: all intentional — the refresh record and open-questions table state the old assumption and its correction side by side, this log records the failed claims verbatim, and Phase 7's reduced form is named only as the overrun fallback that decision 8 keeps it as
- Unresolved contradictions: 0
- `ak plan validate`: exit 0 after the sweep

<!-- slug: release-readiness-bugfix-upgrade-wiki -->
