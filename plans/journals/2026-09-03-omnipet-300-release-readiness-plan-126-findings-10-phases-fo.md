---
title: "OmniPet 3.0.0 release-readiness plan: 126 findings, 10 phases, four review gates"
date: 2026-09-03
summary: Five-scout codebase audit into a 10-phase release plan; red-team killed four of my own assumptions; Bash tool dead all session
---

# OmniPet 3.0.0 release-readiness plan: 126 findings, 10 phases, four review gates

## What happened

Full-codebase release-readiness review of OmniPet (Paper plugin, Java 21, `omnipet-core` + `omnipet-paper`), then a 10-phase plan for 3.0.0. Plan dir `plans/260903-1813-release-readiness-bugfix-upgrade-wiki/` (`plan.md` + `phase-01..10`), scaffolded through `ak plan create` / `ak plan add-phase`. Final state: `ak plan validate` exit 0, `ak plan reindex --apply --yes` clean, `ak plan status` = 10 phases / 153 tasks, ~19d serial effort (~12d critical path if the three tracks are staffed in parallel).

Baseline verified before planning: `gradlew clean build` green, 210 suites / 1116 tests (core 369, paper 747), artifact `build/release/OmniPet-3.0.0-SNAPSHOT.jar`, 49 files uncommitted (idle-play feature, also green).

Five read-only scouts (core, sagas, runtime, ux, wiki) produced 126 findings — 9 P1/High, 40 P2/Med, 77 P3/Low — in `plans/reports/from-scout-*-report.md`. Headline P1s: static shared SnakeYAML `Yaml` at `YamlDocuments.java:14-15` raced by async Paper loads, while the read path quarantines the player file on *any* RuntimeException (`FilePlayerStateRepository.java:135-140`) — a transient race becomes permanent data lockout; ready placed eggs that never hatch (`PlacedEggView.onReady` fires only at the exact tick transition); egg duplication/griefing because `PlacedEggListener` persists at NORMAL before HIGH/HIGHEST protection plugins cancel and `onBreak` has no `isEggBlock`/owner gate; slot-purchase withdraw held inside the player file lock while blocking up to 10s on `callSyncMethod` (server-wide tick freeze); MythicMobs kill-identity bridge binding through the killed entity's NMS classloader (permanent silent unavailability).

Four gates ran against the draft: red-team (6 Critical / 9 Major / 6 Minor / 11 verified-OK — every Critical and Major applied), validation interview, a mechanical coverage audit (all 126 findings traced; 21 gaps + 6 sub-item gaps + 4 missing split candidates folded in), and a consistency sweep (15 contradictions, all applied).

## Corrections the gates forced on my own draft

- bStats as an `implementation` dependency was unimplementable here: no Shadow plugin, the distribution JAR is a hand-rolled `zipTree` merge (`omnipet-paper/build.gradle.kts:71-84`), so it would ship unrelocated and break metrics for every other plugin bundling bStats — and `checkDistributionArtifact` has no `org/bstats/` prefix to catch it. Plan now vendors the single-file `Metrics.java` and adds that forbidden prefix.
- Releasing the player lock around the provider call would have written a stale pre-withdrawal snapshot (`SlotUnlockService.java:255,262`): money already gone, grant lost or `StaleRevisionException`. Plan now re-reads player state + journal after re-locking and routes any divergence to `UNKNOWN_REQUIRES_RECONCILIATION`.
- The max-health item's "breaks on 1.21.3+" framing is contested by the repo's own compatibility CI, and the `Registry.ATTRIBUTE.get(minecraft("max_health"))` fallback I had proposed returns null on the 1.21.0/1.21.1 floor (the key was `generic.max_health` before the 1.21.2/1.21.3 flattening), where `attribute == null ? 20` would silently treat every player as 20 max HP. Phase 1 now records the CI matrix status before Phase 4 chooses a fix.
- "setDropItems(false) stays at LOWEST" described a structure that does not exist — all nine `PlacedEggListener` handlers are NORMAL with `ignoreCancelled = true`, which also means MONITOR handlers need no manual `isCancelled()` re-check.
- The "disjoint packages" parallelism claim was false: `OmniPetPlugin.java` and `paper-plugin.yml` are shared across tracks. Plan now assigns single ownership per file.

## Decisions (user, binding)

Paper support 1.21.x broadly, no paper-api bump. Ready placed egg: auto-hatch retry **and** a CLAIM menu action **and** breaking a READY egg grants the pet. License: All Rights Reserved. Telemetry: bStats + update checker both on by default, both disableable in `config.yml`.

## Tooling failure worth remembering

The Bash tool was dead for most of the session — every call returned "(Bash completed with no output)" with no filesystem side effects (`echo`, `ls`, `ak`, redirects alike). After compaction it started returning `/usr/bin/bash: -c: line 182: unexpected EOF while looking for matching '"'` even for an unquoted `git -C ... rev-parse`. Workaround: drove a live PowerShell tab through the claude-terminal MCP `tab_send` / `tab_read_output`, which preserved the skill's rule that only the `ak` CLI creates plan files. Subagents hit the same wall, so later prompts instructed them to work Read/Glob/Grep-only.

Terminal input replay concatenated a stale buffered line onto a new one and re-ran `ak plan add-phase` nine times, producing `phase-11`…`phase-19` stubs. Recovery: read two stubs to confirm they were untouched, `Remove-Item phase-1[1-9]-*.md`, `ak plan reindex --apply --yes`. Two CLI facts learned the hard way: `ak plan reindex` is dry-run unless `--apply`, and `ak plan validate` fails without an explicit plan path.

## Next steps

Hand off to `/ak:cook plans/260903-1813-release-readiness-bugfix-upgrade-wiki`. Phase 1 (2h) commits the idle-play work and answers the five open questions later phases depend on — chiefly the `compatibility` CI job status and the compile-range vs certified-range split. Phase 2 owns the migration contract (one-way on-disk changes, one log line each, back up `plugins/OmniPet/` before the first 3.0.0 boot, no supported downgrade after it). Plan `260806-2047-license-and-ip-control` stays blocked until Phase 7's `OmniPetPlugin` split and Phase 10's LICENSE land.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
