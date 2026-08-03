---
phase: 6
title: "Docs and verification"
status: pending
priority: P1
effort: "0.5d"
dependencies: [2, 3, 4, 5]
---

# Phase 6: Docs and verification

## Overview

Prove the build, then update only the docs whose claims actually changed. This repo distinguishes "shipped" from "certified" — keep that discipline. Nothing in this plan is live-server certification.

## Requirements

- Functional: clean build green; docs match shipped behavior; CHANGELOG records the `gui:` config addition and the pet-click feature.
- Non-functional: no doc claims live certification; the compatibility matrix is untouched (no Paper API surface changed).

## Architecture

Docs needing edits, each with the specific stale claim to verify against code before editing:

| File | Stale claim after this plan | Fix |
| --- | --- | --- |
| `docs/configuration.md` | No `gui:` section documented | Add the full `gui:` schema: feedback toggles, sounds, rate limit, page sizes, prompt timeout. State that it is optional and lenient, and that an absent section reproduces prior behavior. |
| `docs/commands-and-permissions.md` | Vault row does not mention search or sort | Note vault search/sort and that right-click on a rendered pet opens management. |
| `docs/roadmap.md` | Two places: `:39` "Entity interaction and riding — Deferred … no interact listener routes through it", **and `:48`** "does not ship … entity click interaction" in the not-shipped paragraph | Split the `:39` row — entity **click** ships, **riding** stays deferred with no `RideService`. Remove entity click from `:48` while leaving riding and passive triggers. Do not let the click feature imply riding. |
| `docs/compatibility.md` | Claims no new Paper API surface | **This plan adds two.** See below. |
| `docs/troubleshooting.md` | No guidance for silent feedback or a bad `gui:` value | Add: feedback not audible (master switch, per-category, client volume), unknown sound name behavior, `gui:` clamp warnings, which `gui:` keys are restart-only, and why clicking another player's pet does nothing. |
| `docs/getting-started.md` | Does not mention vault sort/filter or pet click | One line each in the first-run walkthrough. |
| `README.md` | Feature bullets predate feedback, sort/filter, and click; build metrics predate this plan | Update the vault and interaction bullets; refresh metrics from the real build. |
| `CHANGELOG.md` | — | Entries for the `gui:` section (noting restart-only keys), audible/visual feedback, vault sort/filter, pet click, the hub slot-return **bug fix**, and the countdown/decimal deduplication. |

### `docs/compatibility.md` is NOT untouched

An earlier draft claimed no Paper API surface changed. That is **false**:

- Phase 2 introduces `org.bukkit.Sound` and `Registry.SOUNDS`.
- Phase 4 introduces `PlayerInteractEntityEvent` and registers a new event handler.

`Sound`'s shape (enum versus registry-backed) is exactly the kind of thing that shifts across Paper versions, and `compileCompatibilityJava` (`omnipet-paper/build.gradle.kts:54-65`) compiles against a *configurable* API version. At minimum, record the `compileCompatibilityJava` result against both `1.21` and `1.21.1` — both are already in the Gradle cache, so this is cheap. Update the compatibility notes to state which API surfaces are newly relied upon.

`docs/migration.md` is untouched: no schema or data-format change.

## Implementation Steps

1. Run `gradlew.bat clean build --no-daemon --console=plain`. Record task count, suite/test counts per module, JAR byte size, and SHA-256 — the evidence format `README.md` already uses.
2. If anything fails, fix the cause. Do not weaken or skip a test to go green.
3. Confirm the guard tasks pass **for real**, not incidentally:
   - `checkCoreBoundary` — no Bukkit/Adventure import reached `omnipet-core`. This plan should add **nothing** to core; confirm that.
   - `checkDistributionArtifact` — the JAR gained no new bundled dependency. Feedback uses Paper/Adventure API already on the classpath.
   - `checkBranding`, `checkGradleOnly`.
4. Run `compileCompatibilityJava` against `1.21` **and** `1.21.1` and record both results. Two new API surfaces (`Sound`/`Registry.SOUNDS`, `PlayerInteractEntityEvent`) justify this; both versions are already cached.
5. Manual smoke checklist, recorded in `plans/reports/` with each item mapped to its automated coverage, and explicitly marked unrun if no live server is available:
   - **BLOCKING:** confirm a Paper `Interaction` entity actually delivers `PlayerInteractEntityEvent` (not only the At-variant) when it is a passenger of an invisible marker. Phase 4's design depends on it.
   - **BLOCKING:** confirm one right-click opens exactly one management screen — the off-hand double-fire is the most likely regression.
   - Toggle a pet with feedback on, then with `gui.feedback.enabled: false`
   - Spam a control; confirm the rate limit holds and no sound reaches nearby players
   - Set `gui.vault.petsPerPage: 99`; confirm the clamp warning and a working 45-slot page
   - Confirm a restart-only key does **not** change on `/pet admin reload`, matching the docs
   - Delete the `gui:` section; confirm behavior matches the pre-plan baseline
   - Cycle every sort and filter on a 100+ pet vault; confirm no pet duplicates or vanishes across pages
   - Right-click own pet; right-click another player's pet; click a vanilla mob
   - Open slot purchase from the hub, cancel, confirm return to the hub
6. Update the docs in the table. Verify each claim against code, not against this plan.
7. Update `README.md` metrics from step 1's real numbers.
8. Run `/ak:journal` for the session record.

## Success Criteria

- [ ] Clean build passes: zero failures, zero errors, zero skips.
- [ ] All four guard tasks pass; JAR gained no new bundled dependency; `omnipet-core` gained nothing.
- [ ] `compileCompatibilityJava` recorded for both `1.21` and `1.21.1`.
- [ ] Manual smoke checklist recorded in `plans/reports/`, each item mapped to automated coverage. **The two BLOCKING items must be run on a live server before Phase 4 is called done.**
- [ ] Every doc row in the table is updated and matches code.
- [ ] `README.md` metrics reflect the actual build output.
- [ ] `CHANGELOG.md` records the `gui:` addition (with restart-only keys), the pet-click feature, and the hub slot-return **bug fix** explicitly.
- [ ] `docs/roadmap.md` edited at **both** `:39` and `:48`; entity click ships while riding stays deferred.
- [ ] `docs/compatibility.md` names the two newly-relied-upon API surfaces.
- [ ] `configuration.md` states which `gui:` keys are restart-only.
- [ ] No doc claims live-server or vendor certification.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Docs written from the plan rather than the code | Each row names the exact stale claim; re-read the code before editing. |
| Metrics copied from the old README | Metrics come only from step 1's recorded output. |
| Shipping entity click implies riding also shipped | Both roadmap lines (`:39`, `:48`) are edited; riding stays deferred with `riding=false` unchanged. |
| Claiming compatibility is untouched while adding two API surfaces | Explicitly corrected; `compileCompatibilityJava` run against two versions. |
| Docs claim live reload for restart-only keys | The reload table from Phase 2 is the source of truth; a smoke item verifies a restart-only key does not change on reload. |
| A guard task passes for the wrong reason | Assert core gained nothing and the JAR gained no entries, rather than trusting a green tick. |
| Overstating readiness | Riding, passive triggers, Active Party, rename, admin target mode, vendor and live certification all stay deferred. |
