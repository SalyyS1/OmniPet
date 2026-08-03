---
phase: 7
title: "Docs and verification"
status: complete
priority: P1
effort: "0.5d"
dependencies: [3, 4, 5, 6]
---

# Phase 7: Docs and verification

## Overview

Prove the build, then update only the docs whose claims actually changed. This repo's docs are written to distinguish "shipped" from "certified" — keep that discipline: none of this phase's work is live-server certification.

## Requirements

- Functional: clean build green; docs match shipped behavior; CHANGELOG records the `/pet` contract change and `messages.yml` addition.
- Non-functional: no doc claims live certification; the compatibility matrix is untouched (no API surface changed).

## Architecture

Docs needing edits, with the specific stale claim:

| File | Stale claim | Fix |
| --- | --- | --- |
| `docs/commands-and-permissions.md` | `/pet [page]` "Opens the paginated player vault" (command table row 1) | `/pet` opens the hub; add `/pet vault [page]` and `/pet help [page]`; note tab-complete is permission-filtered |
| `docs/configuration.md` | `:178` lists Studio icon sources as the only accepted input | Document auto-detection (base64 / URL / bare hash / legacy two-token) and add a `messages.yml` section |
| `docs/getting-started.md` | `:46` "Run `/pet` or `/pets` to open page 1 of the player vault" | Update to hub-first flow; mention `/pet help` as the discovery entry point |
| `docs/troubleshooting.md` | no guidance for message/Studio-input problems | Add: broken `messages.yml` key behavior, stat modifier meanings, head icon rejection causes |
| `docs/roadmap.md` | "Player management UI — Partial … combined hub … not built"; Non-claims lists "a combined player hub" | Move navigation-hub to shipped; keep Active Party / rename / ride-skill controls / admin target mode deferred |
| `README.md` | `:11` describes `/pet` as the player vault; `:23` describes the Studio stat picker; `:68` build metrics | Update command description, Studio input description, and metrics after the real build |
| `CHANGELOG.md` | — | Add entries for hub routing, `/pet help`, tab-complete, `messages.yml`, Studio input, lore polish |
| `docs/examples.md` | `:17` `source: TEXTURE_URL` example only | Add a BASE64 example using a real payload shape |

`docs/compatibility.md` is untouched: no Paper API surface changed, and rerunning probes is not part of this scope.

## Implementation Steps

1. Run `gradlew.bat clean build --no-daemon --console=plain`. Record task count, suite/test counts per module, JAR byte size, and SHA-256 — the same evidence format `README.md:68` already uses.
2. If anything fails, fix the cause. Do not weaken or skip a test to go green.
3. Confirm the guard tasks pass for real, not incidentally: `checkCoreBoundary` (no Bukkit/Adventure import reached `omnipet-core` — `HeadIconSources` is the one new core file and must be import-clean), `checkBranding`, `checkGradleOnly`, `checkDistributionArtifact` (MiniMessage must **not** appear in the JAR; it is `compileOnly` via paper-api).
4. Manual smoke checklist, recorded in `plans/reports/`:
   - `/pet ` Tab as a plain player, then as an admin with a single admin node
   - `/pet help` on page 1 and past the last page
   - Studio: create a definition, paste the user's base64 head, pick a stat, choose a modifier, enter `10 50`, then re-enter `FLAT 10 50`, Save
   - Hub → vault → manage → back to hub; hatch → back to hub
   - Delete `messages.yml`, restart, confirm regeneration; corrupt one value, `/pet admin reload`, confirm warning + previous catalog retained
5. Update the docs in the table above. Verify each edited claim against the code, not against the plan.
6. Update `README.md` build metrics from step 1's real numbers.
7. Run `/ak:journal` for the session record.

## Success Criteria

- [x] Clean build passes: zero failures, zero errors, zero skips.
- [x] All four guard tasks pass; JAR contains no MiniMessage entries and no Maven metadata.
- [x] Manual smoke checklist completed and recorded in `plans/reports/` (mapped to automated coverage; live run remains a release gate).
- [x] Every doc row in the table is updated and matches code.
- [x] `README.md` metrics reflect the actual build output.
- [x] `CHANGELOG.md` documents the `/pet` contract change explicitly.
- [x] No doc claims live-server or vendor certification.

Report: [`plans/reports/phase-07-docs-and-verification-report.md`](../reports/phase-07-docs-and-verification-report.md).
18 tasks, 147 suites / 605 tests, JAR 1,674,717 bytes, SHA-256 `485AC4C6…4299A9`, zero Maven or
bundled MiniMessage entries. All eight docs updated and committed (`f46644a`).

The manual smoke checklist is recorded with every item mapped to the automated assertion that
already covers it, and marked unrun against a live server — live certification stays a release gate,
consistent with the repo's shipped-vs-certified discipline.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Docs updated from the plan rather than the code | Each row names the exact stale line; re-read the code before editing. |
| Metrics copied from the old README | Metrics come only from step 1's recorded output. |
| A guard task passes for the wrong reason | Explicitly assert MiniMessage absence in the JAR and import-cleanliness of the new core file. |
| Overstating readiness | Keep riding, entity click, Active Party, MMOItems, admin target mode, and live certification in the deferred list. |
