---
phase: 6
title: "Operator upgrades and diagnostics"
status: pending
priority: P2
effort: "1.5d"
dependencies: [2, 4, 5]
---

# Phase 6: Operator upgrades and diagnostics

## Context Links

- Upgrade lists in all four code scout reports (`plans/reports/from-scout-*-report.md`)

## Overview

Ship the operator-facing release features: `/pet version`, `/pet admin diagnose` (surfacing every bridge's health/unavailable detail), bStats metrics and an update checker (both on by default, both with a `config.yml` opt-out), offline-player admin stats, and a configurable studio session timeout.

## Key Insights

- Today the only way to see why a vendor bridge is down is log archaeology; Phase 4 creates per-bridge health + `unavailableDetail()` surfaces that just need one command to read them (extend the `OwnerBuffDiagnostics` pattern).
- No version command, no metrics, no update visibility — standard expectations for a public release.
- Studio timeout is hardcoded 15 min (`PetStudioController:99`); `admin stats` works online-only; `stats` admin area reuses `omnipet.admin.reload`.
- bStats/update checker are the plugin's first outbound network calls. **Decided (validation):** both enabled by default, both switchable off in `config.yml`.
- Dependency locking is active (three tracked lockfiles, confirmed 2026-09-04), one more reason bStats is vendored rather than declared: no lockfile churn, no `--write-locks` run this release. No bStats service ID exists in the repo; unless the operator supplies one (`plan.md` open question 3), the blank-ID no-op path is the shipping default, not a fallback.

## Requirements

- Functional: `/pet version` (version, git commit if embedded, vendor bridge summary); `/pet admin diagnose` (per-bridge state: healthy / quarantined+detail / absent, plus storage + queue depth); bStats with standard + pet-count custom chart, on by default with `metrics.enabled` opt-out; update checker on by default with `updateCheck.enabled` opt-out, console notice + admin join notice; `admin stats <player>` works for offline players via repository read; `gui.studio.sessionTimeout` config key; dedicated `omnipet.admin.stats` permission (default op, documented as a split from `omnipet.admin.reload`).
- Non-functional: all network calls async, failures logged once then silent, both fully disableable; first-run console line naming what is sent and how to turn it off.

- bStats is **vendored, not added as a dependency**. This repo has no Shadow plugin: the distribution JAR is a hand-rolled merge (`omnipet-paper/build.gradle.kts:71-84`, `from(configurations.runtimeClasspath ... zipTree)`), so any new `implementation` dependency ships **unrelocated** and would break metrics for every other plugin that bundles bStats — and `checkDistributionArtifact`'s forbidden-prefix list (`build.gradle.kts:83-92`) has no `org/bstats/` entry, so the mistake would ship silently. Copy bStats' single-file `Metrics.java` into `io.github.salyvn.omnipet.paper.metrics` (the packaging path bStats documents for Bukkit: no dependency, no Shadow, no lockfile churn) and add `org/bstats/` to the forbidden prefixes so an accidental dependency fails `check`. Adopting Shadow instead is a separate packaging change (apply the plugin, `archiveClassifier=""`, re-point `distributionJar` at `build.gradle.kts:9`, `checkDistributionArtifact`, and `copyReleaseArtifact`) — do not smuggle it into this phase.
- Every new config key must round-trip through `OmniPetConfigLoader.encode()` in the same commit that adds it. `gui.studio.sessionTimeout`, `metrics.enabled`, and `updateCheck.enabled` are exactly the bug class Phase 5 is fixing for `render.nameplateStatus` — a key the loader reads but never re-encodes, so legacy migration silently drops it.

## Architecture

- `VersionCommand` + `DiagnoseCommand` register through `OmniPetCommandTree` (Phase 5's derivation gives help/suggestions/permissions for free).
- `BridgeDiagnostics` aggregates the five reflective bridges' health snapshots (data already exists post-Phase 4) + renderer fallback reasons + queue depth from `PerPlayerTaskQueue`.
- bStats vendored as a single class under `io.github.salyvn.omnipet.paper.metrics`; metrics switch in `config.yml` honored in addition to bStats' global toggle.
- Update checker: single async HTTP GET on enable + daily timer, comparing semver; endpoint stored in config so it can be repointed. **Decided (validation session 2, 2026-09-04): the default endpoint is the GitHub Releases API, `https://api.github.com/repos/SalyyS1/OmniPet/releases/latest`** (public repo, no token, `tag_name` compared as semver after stripping a leading `v`). It fails closed (one log line, no notices) until `v3.0.0` exists, which Phase 10 creates.

<!-- Updated: Validation Session 2 - update-check endpoint fixed to the GitHub Releases API -->


## Related Code Files

- Create: `omnipet-paper/.../command/VersionCommand.java` (or fold into `OmniPetCommandTree` handler), `omnipet-paper/.../diagnostics/BridgeDiagnostics.java`, `omnipet-paper/.../diagnostics/UpdateChecker.java`, `omnipet-paper/.../metrics/Metrics.java` (vendored bStats single-file class)
- Modify: `build.gradle.kts` (add `org/bstats/` to `checkDistributionArtifact` forbidden prefixes — no dependency added)
- Modify: `omnipet-paper/.../command/OmniPetCommandTree.java`, `OmniPetCommand.java`, `OmniPetAdminCommand.java`, `buff/OwnerBuffDiagnostics.java` (fold into BridgeDiagnostics surface), `config/OmniPetConfig.java` + `OmniPetConfigLoader.java` (metrics/update-check/studio-timeout keys — including `encode()`), `studio/bukkit/PetStudioController.java` (timeout from config), `economy/SlotTransactionAdminController.java` + stats path for offline reads, `paper-plugin.yml` (new permission nodes — note it is processed through Groovy `expand()` at `omnipet-paper/build.gradle.kts:44-48`, so a literal `$` anywhere in it breaks the build), `lang/vi.yml` (new command strings)
- Modify: `OmniPetPlugin.java` (wire diagnose/version/metrics/update checker)

## Implementation Steps

1. `/pet version` + tree node + catalog strings + test.
2. `BridgeDiagnostics` aggregation + `/pet admin diagnose` rendering (chat, one line per bridge) + tests with faked bridge states.
3. Offline `admin stats` via async repository snapshot (bounded by Phase 2's read limits) + dedicated `omnipet.admin.stats` node.
4. Studio timeout config key with 15-min default; reload-safe.
5. Vendor bStats' `Metrics.java`, wire the config switch, add `org/bstats/` to the forbidden prefixes, and verify the shaded `build/release` JAR contains the class under the OmniPet package (not `org/bstats/`).
6. Update checker: async fetch, semver compare, console + admin-join notice, config endpoint and switch.
7. Docs keys for Phase 8 to pick up; full build. (`invalidateDefinitions()` on reload is owned by Phase 3 step 1 — do not duplicate it here.)

## Todo

- [ ] /pet version
- [ ] /pet admin diagnose over all bridges
- [ ] Offline admin stats + dedicated `omnipet.admin.stats`
- [ ] Configurable studio timeout
- [ ] bStats vendored (not a dependency) + `org/bstats/` forbidden-prefix guard + config switch
- [ ] Update checker on by default + config switch; default endpoint = GitHub Releases API for this repo
- [ ] Green: full build

## Success Criteria

- [ ] Operator can see plugin version and every bridge's health without reading logs
- [ ] Diagnose output distinguishes vendor-absent / quarantined(detail) / healthy per bridge
- [ ] `admin stats <player>` answers for an offline player
- [ ] Metrics and update checks can be fully disabled in config
- [ ] Every new config key round-trips through `encode()` (asserted by test)
- [ ] Studio timeout configurable and reload-aware
- [ ] Distribution JAR contains no `org/bstats/` path (guard in `checkDistributionArtifact`)

## Risk Assessment

- Packaging mistakes surface only in the shaded artifact — verify the `build/release` JAR contains `Metrics` under the OmniPet package and no `org/bstats/` path at all; boot that JAR once in Phase 9.
- Vendoring `Metrics.java` means no automatic upstream updates — record the bStats version copied, in the class Javadoc, so a future refresh is mechanical.
- A bStats service ID must exist for OmniPet before metrics can post. If one is not registered yet, ship the switch and the class with the ID as a config value and leave it blank (the class no-ops), rather than blocking the phase.
- Update-check endpoint may 404 until a public listing exists — the checker must fail closed (one log line, no player-facing notice) rather than warn every day.

## Security

- First outbound network calls: update checker + bStats, both on by default and both switchable off. Payload is bStats standard metrics plus a pet-count chart — no player identifiers, no world data. Disclosed in `config.yml` comments, the first-run console line, Phase 8's docs, and the Phase 10 release notes.
