---
phase: 1
title: "Baseline and settle in-progress work"
status: in-progress
priority: P1
effort: "3h"
dependencies: []
---

# Phase 1: Baseline and settle in-progress work

## Overview

Commit the uncommitted idle-play feature (49 modified/new files, builds green) so every later phase starts from a clean tree, land the one-line max-health hotfix that turns the red `compatibility` job green, and record the verified baseline the certification gate will compare against.

## Key Insights

- Working tree has an in-progress but complete idle-play feature: `config.yml` `idle-play` block, `IdlePlaySettings`, `PetVanityParticleSink` + Bukkit impl, `IdleBehaviour.playTarget`, particle cadence in `PaperRuntimePetState.advance`, command-tree arg renames (`<player_name>`/`<ID>`), `release reconcile <player_name>`, doc updates. Build is green with it (re-verified 2026-09-04: 210 suites / 1116 tests, 0 failures). The diff was read in full on 2026-09-04: no blocking defect; the three behavioral notes (particles on by default, failure-sink stage, idle-before-movement ordering) are in `plan.md`'s refresh record and belong to Phases 4 and 9.
- Later phases modify the same files (`IdleBehaviour`, `MovementController`, `OmniPetPlugin`, command tree, docs); an uncommitted mix would make review and rollback impossible.
- **The `compatibility` CI job is red on `main`** (run 31252250448, every push since 2026-08-06): `SkillTriggerListener.java:197` references `Attribute.GENERIC_MAX_HEALTH`, which the Paper `1.21.11` and `26.x` API jars no longer declare (`Attribute` is an interface exposing `MAX_HEALTH`). Reproduced locally (`build/compat-1.21.11-260904.log`; the 1.21.11 API jar is now in the Gradle cache). `Damageable.getMaxHealth()` exists on every build in the matrix (`javap` on the 1.21.11 jar) and is the fix the binding decision names.
- Dependency locking is active: `omnipet-core/gradle.lockfile`, `omnipet-paper/gradle.lockfile`, and `settings-gradle.lockfile` are tracked. An earlier draft of this phase said there was no lockfile; that was wrong.

## Requirements

- Functional: idle-play work committed as one or two conventional commits (feature + docs), tree clean afterwards.
- Non-functional: no unrelated changes smuggled in; `.claude-terminal` and other tool-state files excluded from feature commits.

## Related Code Files

- Modify (commit only, no edits): all 49 files in `git status`, notably `omnipet-core/.../runtime/IdleBehaviour.java`, `omnipet-paper/.../runtime/*`, `omnipet-paper/.../command/*`, `config.yml`, `docs/*`.
- Modify (hotfix, two lines): `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/skill/SkillTriggerListener.java:197-198`.

## Implementation Steps

1. `git status` + `git diff --stat`; group changes: idle-play feature code, command arg renames, doc updates, plan/report files, tool-state noise (`.claude-terminal` never goes in).
2. Run full `gradlew clean build` to reconfirm green before committing (2026-09-04 result: green, 210 / 1116).
3. Commit feature code (`feat(runtime): let idle pets play with vanity particles` or similar), then docs/command polish as a second commit if cleanly separable. Plan, report, and journal files go in a third `docs(plans):` commit or stay uncommitted — never mixed into the feature commit.
4. **Hotfix the red matrix.** Replace `player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)` and `attribute == null ? 20 : attribute.getValue()` with `double maximum = player.getMaxHealth();` — keep the `maximum <= 0` guard. Run `gradlew :omnipet-paper:compileCompatibilityJava -PcompatibilityPaperApiVersion=1.21.11-R0.1-SNAPSHOT` (cached) and the default build. Commit `fix(skills): read max health through an API every 1.21 build has`. Push, then confirm the `compatibility` job is green on all five matrix points before any track fans out.
5. Record baseline numbers in this file's Todo (suite/test counts, JAR path + size + SHA) for Phase 9 comparison — pre-filled below from the 2026-09-04 run; re-record if step 2 differs.
6. Compile range vs certified range: **decided 2026-09-04 (operator)** — keep the full `compatibility` matrix. Two separate facts for Phase 8: compiles against Paper 1.21 → 26.2 (the 26.x points on a CI-provisioned Java 25 toolchain); certified range is whatever Phase 9 measures. Nothing to change in `build.yml` beyond the hotfix turning it green.
7. Dependency locking: recorded — three tracked lockfiles, locking active, keep. Nothing to decide.
8. Watch the Pages deploy that the docs commit triggers. If it times out in `deployment_queued` again, `gh run rerun <id>`; the permanent `timeout-minutes` guard is Phase 8's. Confirm `https://salyys1.github.io/OmniPet/wiki-stats.json` changes after the deploy.
9. Verify `git status` clean.

## Todo

- [x] Reconfirm green build on current tree (2026-09-04 06:10, with the hotfix applied: 210 suites / 1116 tests, 0 failures; `compileCompatibilityJava` green against `1.21.11-R0.1-SNAPSHOT` and the `1.21-R0.1` floor)
- [ ] Commit idle-play feature + docs (conventional commits, no AI references)
- [ ] Max-health hotfix committed; `compatibility` green on all five matrix points in CI
- [x] Record baseline: 210 suites / 1116 tests (core 75 / 369, paper 135 / 747), `build/release/OmniPet-3.0.0-SNAPSHOT.jar` 2,014,223 bytes, SHA-256 `4ffa336a…d2e7`, 1062 classes (2026-09-04)
- [x] Record `compatibility` job status: red since 2026-08-06 — `1.21-R0.1` green; `1.21.11`, `26.1.1`, `26.1.2`, `26.2` fail on `GENERIC_MAX_HEALTH`
- [x] Record the compile-range vs certified-range answer: keep the full matrix; "compiles against 1.21 → 26.2", certified range = Phase 9 evidence (operator, 2026-09-04)
- [x] Record the dependency-locking decision: lockfiles tracked, locking active, keep
- [ ] Pages deploy for the docs commit succeeded; live stats file updated
- [ ] Clean `git status`

## Success Criteria

- [ ] `git status` shows no uncommitted source changes
- [ ] Baseline recorded and reproducible with `gradlew clean build`
- [ ] `compatibility` job green on every matrix point, so Phase 4 starts from a green baseline
- [x] Compile range and certified range recorded as two separate facts, so Phase 8 documents Paper support without guessing
- [ ] Live docs site serves the committed docs

## Risk Assessment

- Idle-play work may hide latent defects (scouts flagged none; the 2026-09-04 read found none blocking). Signal: test failure during the pre-commit build. Response: fix before committing; do not stash and carry the mix forward. A defect found after the commit is a normal post-release fix, not a 3.0.0 gate.
- `Damageable.getMaxHealth()` is deprecated-but-present across the whole matrix. Signal that the assumption broke: `compileCompatibilityJava` fails on a future matrix point with `getMaxHealth` missing. Response: switch to `Registry.ATTRIBUTE` keyed per version behind a small resolver — never the null-means-20 fallback the plan already rejects.
- The Pages queue timeout was GitHub-side; if the re-triggered deploy hangs again, the site stays one commit stale until Phase 8 — acceptable, since nothing operator-facing changed in that commit.
