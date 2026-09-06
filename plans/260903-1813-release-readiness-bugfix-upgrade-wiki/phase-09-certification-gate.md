---
phase: 9
title: "Certification gate"
status: pending
priority: P1
effort: "1.5d"
dependencies: [7, 8]
---

# Phase 9: Certification gate

## Context Links

- Absorbed prior work: `plans/260805-0249-performance-gameplay-wiki-roadmap/part-h-certification.md` (Phase 15 — live-server certification)
- Baseline from Phase 1 (suite/test counts, artifact)

## Overview

Prove the release on a live Paper server with real numbers: spark measurements at pet-count tiers, vendor-absent boot, all-switches-off silence, and durability drills (process kill mid-saga, egg placement round-trips). No release cut without this evidence.

## Key Insights

- Every performance claim so far is code-reading; part-h requires numeric proof (tick time, entity count, bandwidth at 10/50/100 pets).
- The optional-vendor matrix (MythicMobs, MythicLib, ModelEngine, Vault, PlayerPoints, LuckPerms all absent) is the plugin's core promise and exactly where Phase 4's quarantine rework landed.
- Phase 3's placed-egg rework needs live drills: process-kill durability; breaking a *not-yet-ready* egg returns the same item; breaking a *READY* egg grants the pet; CLAIM from the placed-egg menu grants the pet; no dup under a protection plugin; vault-full wait-then-deliver.

## Requirements

- Functional: all Tier 1 drills pass on the shaded `build/release` JAR (not classes dirs). Tier 2 items may be waived with a recorded note.
- Non-functional: results recorded with numbers in this phase file's Todo + `docs/compatibility.md`; failures loop back to the owning phase before release.

## Evidence tiers

ModelEngine is paid and the MythicMobs line is premium, so a gate that requires them can deadlock Phase 10. Split the evidence:

- **Tier 1 — blocking.** Vendor-absent boot, all-switches-off silence (including zero outbound connections), the durability drills, the placed-egg drill set, and the perf tiers on the head renderer. None of these needs a paid plugin.
- **Tier 2 — waivable with a recorded note.** The ModelEngine-vs-head spark comparison and the live MythicMobs kill-EXP / `/mm reload` checks. A waiver is not silence: `docs/compatibility.md` must then say "untested against ModelEngine <version>" — never "supported". **Decided 2026-09-04 (operator): both vendors will be available, so Tier 2 is expected to run for real**; the waiver survives only as the fallback if a build proves unusable on the day, and then it must be recorded with the exact version that was tried.

## Architecture

Local Paper server with spark installed. Because the supported range is 1.21.x-wide (binding decision in `plan.md`), certify on **two** builds: the oldest supported (1.21.0/1.21.1) and the newest current 1.21.x — that pair is what proves the version-agnostic API work in Phase 4. Scenario scripts: summon N pets via admin grants across test accounts; measure idle vs following vs skill-casting. Second run with zero optional plugins; third with every OmniPet switch off (expect total silence: no particles, no nameplates, no log spam) — and with `metrics.enabled`/`updateCheck.enabled` off, no outbound connection either. Durability: `taskkill /F` mid incubation-start, mid slot-purchase, mid release; verify recovery on next boot per saga matrices.

## Related Code Files

- Modify: `docs/compatibility.md`, `docs/roadmap.md`, `CHANGELOG.md` (real numbers), this phase file (evidence log); `.github/workflows/build.yml` + `omnipet-paper/build.gradle.kts` for step 8's drift guard only
- Apart from that guard, no production code changes are expected; regressions found here are fixed in their owning phase and re-certified

## Implementation Steps

1. Boot shaded JAR on clean Paper + spark; record version/flags. Repeat the boot check on both ends of the supported 1.21.x range (low-health trigger exercised on each, since that is the API-drift canary).
2. Perf tiers: 10/50/100 active pets — spark tick report, entity counts, rough bandwidth; repeat with ModelEngine models vs head renderer.
3. Vendor-absent boot: zero optional plugins; assert clean enable, `/pet admin diagnose` shows absent (not quarantined) per bridge, all features degrade per docs.
4. Switches-off run: every toggle off → no visible/audible/log output from OmniPet. The list now includes `idle-play.enabled: false` (on by default since the Phase 1 commit; a playing pet otherwise broadcasts a world-visible particle burst every 10 ticks), `gui.feedback.enabled`, `render.nameplates`, `metrics.enabled`, and `updateCheck.enabled`.
5. Durability drills: process kill mid egg-escrow, mid slot purchase (external pending), mid release delivery; recovery matrices hold on reboot. Placed-egg set: breaking a not-yet-ready egg returns the same item, breaking a READY egg grants the pet, CLAIM from the menu grants the pet, protection-cancel produces no dup, instant-hatch works, an offline owner's ready egg hatches after join, vault-full delivers once a slot frees. Also boot once against a copy of a real 2.x `plugins/OmniPet/` directory and record each migration log line — this is the only place the migration contract gets exercised end to end.
6. MythicMobs kill-EXP live check (kill identity fix) + `/mm reload` skill re-cast check.
7. Record all numbers; update compatibility/roadmap/CHANGELOG; file regressions to owning phases if any.
8. Add the drift guard that keeps this from rotting: extend `compileCompatibilityJava` (or add a CI matrix job) to compile against both ends of the supported range, so a future `Attribute`/`Registry`/`Material` rename fails a build instead of a player's first damage event. Manual two-build certification proves today; the guard proves tomorrow.

## Todo

- [ ] Spark numbers at 10/50/100 pets, head renderer (Tier 1)
- [ ] ModelEngine-vs-head comparison (Tier 2 — vendor confirmed available; waiver only if the build fails on the day)
- [ ] Live MythicMobs kill-EXP + `/mm reload` checks (Tier 2 — vendor confirmed available; waiver only if the build fails on the day)
- [ ] Boot + low-health trigger green on oldest and newest supported 1.21.x
- [ ] Vendor-absent boot clean; diagnose shows absent per bridge
- [ ] All-switches-off total silence (including `idle-play.enabled: false` and zero outbound connections)
- [ ] Process-kill durability x3 sagas
- [ ] Migration log lines observed on first 3.0.0 boot against a 2.x data directory
- [ ] Placed-egg drill set (not-ready break returns item, READY break grants pet, CLAIM grants pet, no-dup, instant-hatch, offline, vault-full)
- [ ] Numbers recorded in docs + evidence log here
- [ ] Paper-range drift guard added so 1.21.x compatibility is checked by CI, not by memory

## Success Criteria

- [ ] Every Tier 1 item has recorded evidence with numbers; every waived Tier 2 item has a note and matching compatibility.md wording
- [ ] A 2.x data directory upgrades on first boot with every migration visible in the log
- [ ] A future Bukkit API rename fails CI rather than a player's first damage event (drift guard in place)
- [ ] No regression open against any earlier phase
- [ ] `docs/compatibility.md` states measured support, not aspiration

## Risk Assessment

- 100-pet tier may reveal budget tuning needs (`runtime.maximumMicrosPerTick`) — tuning config defaults is in scope; engine rework is not (falls to a follow-up plan if needed; record the numbers either way).
- Live vendor versions may differ from the reflective bridges' assumptions — record exact tested versions in compatibility.md; a bridge failing against a current vendor release is a Phase 4 regression, fix before cut.
- Two-version certification doubles the drill time. If only one Paper build is available, certify on the newest and state the untested floor in `docs/compatibility.md` rather than claiming range support the evidence does not cover.
