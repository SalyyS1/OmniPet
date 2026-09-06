---
title: "OmniPet release plan refresh: compat CI red, lockfiles live, docs site stale"
date: 2026-09-04
summary: "Day-two pass over the 3.0.0 release plan with a working shell: red compatibility job promoted to a Phase 1 hotfix, two wrong assumptions corrected, idle-play tree reviewed"
---

# OmniPet release plan refresh: compat CI red, lockfiles live, docs site stale

## What happened

Re-reviewed the OmniPet codebase and the 10-phase release plan at `plans/260903-1813-release-readiness-bugfix-upgrade-wiki/` with a shell that actually worked (yesterday's session had none). Did not re-scout — no commit since `fc22e2c` — but re-confirmed every P1 at its cited line, re-ran the clean build (210 suites / 1116 tests, 0 failures, JAR 2,014,223 bytes / 1062 classes), read the 49-file idle-play working tree end to end, and checked live CI, Pages, repo metadata, and the Gradle cache.

## What changed the plan

- **`compatibility` CI has been red on `main` since 2026-08-06.** Four of five matrix points fail on `SkillTriggerListener.java:197: cannot find symbol GENERIC_MAX_HEALTH`. Reproduced locally against `1.21.11-R0.1-SNAPSHOT`; `javap` on that jar shows `Attribute` as an interface with `MAX_HEALTH` only and `Damageable.getMaxHealth()` present. Yesterday's red team called the P1 framing "contested" — it was right to demand evidence, and the evidence says P1. Hotfix moved into Phase 1 so Phase 4 starts green.
- **Dependency locking is not inert.** Three lockfiles are tracked. Yesterday's plan said there were none. Corrected in plan.md, Phase 1, Phase 6.
- **The docs site is one commit stale.** The Pages run for `9bf8629` timed out in `deployment_queued` after every verify step passed; no `timeout-minutes` on the deploy step. Live `wiki-stats.json` 1016/199 vs committed 1087/207 vs real 1116/210. Phase 8 gets the timeout guard.
- **Repo is PUBLIC with no LICENSE and no tags.** All Rights Reserved on a public repo is now open question 6 and gates the Phase 10 tag push.
- **Idle-play tree:** no blocking defect. Particles are on by default (world-visible, bounded), the particle failure path uses the un-deduplicated failure sink under `MOVEMENT`, and idle state is now computed before the movement step. Handed to Phases 4 and 9.

## Tooling notes

`python3` is not on PATH here (Windows Store alias) — test counts came from an awk pass over the JUnit XML. `gh run view --job <id> --log | grep "error:"` is the fastest way to get a compile error out of a matrix job. The 1.21.11 paper-api jar is now in the Gradle cache, so Phase 1's local compat check is instant.

## Validate session 2 (same day)

Ran the validate gate against the refreshed plan at Full tier: 91 claims checked, 81 verified, 6 failed, 4 unverified. Every failure was a path or line that had moved, not a wrong finding — the worst was Phase 2 planning to delete both `EffectBudget` tests when one of them (`RuntimeFleetBudgetEvidenceTest`) is the 100-pet deterministic-work evidence README and Phase 9 lean on; it now keeps that test with an inlined counter. Contract checks turned up one constraint worth writing down: `new FilePlayerStateRepository(` has 38 call sites, so Phase 2's read bound must be a constant, not a constructor parameter.

Four more operator decisions landed: permission derivation tightens to the tree (disclosed in release notes); wiki stats reach Pages via `workflow_run` from Build; the update checker defaults to the GitHub Releases API for this repo; Phase 7 keeps the full behavioural test rewrite. Consistency sweep across all eleven plan files: zero unresolved contradictions. Plan is cook-eligible.

## Next

Hand off to `/ak:cook plans/260903-1813-release-readiness-bugfix-upgrade-wiki`. Operator answers still needed for open questions 2, 3, 4, 6 — none blocks Phase 1.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
