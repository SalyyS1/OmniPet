---
phase: 8
title: "Wiki polish and docs truth-up"
status: pending
priority: P1
effort: "2.5d"
dependencies: [3, 4, 5, 6, 7]
---

# Phase 8: Wiki polish and docs truth-up

## Context Links

- Findings source: `plans/reports/from-scout-wiki-to-planner-wiki-docs-audit-report.md`
- Absorbed prior work: `plans/260805-0249-performance-gameplay-wiki-roadmap/part-g-wiki-ux.md` (Phases 13-14 accessibility/UX items — verified mostly implemented; residuals listed below)

## Overview

Fix the two shipping renderer defects (escaped pipes break command tables; `###` renders as literal text), close command/config coverage gaps, kill the stale-claim cluster across README/docs/landing, finish the residual part-g UX items, and put wiki verification + stats under CI so drift cannot silently return.

## Key Insights

- `docs/wiki.js` renderer only handles `## `; `renderTable` splits on `|` with no escape — both defects are live on the commands and config pages in EN and VI, and `tools/verify-wiki-render.mjs` cannot see either class.
- `docs/commands-and-permissions.md` is currently more accurate than the wiki that claims authority over it. The durable fix is generating command data from `OmniPetCommandTree` (a JUnit writer producing `docs/wiki-commands.json`), not another hand sync — Phase 5 just made the tree authoritative for the plugin itself.
- Stale-claim cluster: README:3,47,69,87; getting-started.md:5,27,39,50; roadmap.md:5,53; three conflicting test counts (461/699/1087; real: 1116 at baseline); wiki-stats.json hand-generated 2026-08-06.
- Bilingual heading parity is already clean on all 12 pages; the VI leak is hardcoded chrome strings in `wiki.html`.
- Constraints: wiki must work from `file://`; no build step, no ES modules, no fetch for core render.
- The live site is stale (checked 2026-09-04): the Pages run for `9bf8629` timed out in `deployment_queued` after every verify step had passed, so `salyys1.github.io/OmniPet` still serves the `14bb349` docs and a `wiki-stats.json` of 1016 / 199, while the committed file says 1087 / 207 and the build says 1116 / 210. The deploy step has no `timeout-minutes`, so a hung GitHub queue burns the job's default six hours before failing. README's artifact paragraph (1,727,430 bytes / 959 classes, dated 2026-08-03) is also stale against 2,014,223 bytes / 1062 classes.

## Requirements

- Functional: every command/permission/config key shown in the wiki matches code truth; all stale claims corrected; both renderer defects fixed with verify-script coverage; bilingual chrome; landing page presentable (meta, favicon, OG tags, 404, version single-sourced); the upgrade path from 2.x documented.
- Non-functional: `file://` compatibility preserved; verify scripts extended (h3, table column count, internal links, EN/VI parity, stats freshness) and green in CI.

## Architecture

- **Renderer:** add `### ` support (h3, TOC-excluded or included consistently) and `\|` escape in `renderTable`; column-count assert in `verify-wiki-render.mjs`.
- **Command truth pipeline:** a **Gradle task** (not a test) generates `docs/wiki-commands.json` from `OmniPetCommandTree` into `rootProject.layout.projectDirectory.dir("docs")` — names, args, permissions, EN/VI descriptions from the catalog. A JUnit test then **asserts committed == generated and writes nothing**, so drift fails the build. Two traps this avoids: a test that writes the file can never fail on drift (it rewrites it), and the repo's source-reading test idiom is module-relative (`OmniPetCommandTreeContractTest.java:26` uses `Path.of("src/main/resources/paper-plugin.yml")`), so a writer test would land in `omnipet-paper/docs/` and dirty the tree on every `gradlew test`. `wiki-content.js` consumes the committed JSON as a script global (never `fetch`) so `file://` keeps working; `tools/verify-wiki-render.mjs` only checks shape and render — it is Node and cannot read Java.
- **Config coverage:** script parses `config.yml` leaf paths and asserts each appears in the wiki config page or an allow-list; add the ~9 missing sections + reload-table rows (`render`, `idle-play`).
- **Stats pipeline:** `build-wiki-stats.mjs` currently runs in no workflow. Two separate GitHub workflows cannot hand an artifact to each other on independent runs. **Decided (validation session 2, 2026-09-04): shape (a)** — Build uploads a `wiki-stats` artifact; Pages triggers on `workflow_run` of Build (`types: [completed]`, `conclusion == success`) and downloads it by `github.event.workflow_run.id` with `actions/download-artifact`, keeping `workflow_dispatch` and the docs-only `push` path as fallbacks that use the committed file. Rejected: (b) Build committing to `main` with `[skip ci]` (bot commits, loop guard) and (c) Java + Gradle inside the Pages job (adds minutes to every docs deploy). Add

<!-- Updated: Validation Session 2 - stats pipeline shape fixed to workflow_run -->
 `tools/build-wiki-stats.mjs` and the new config-coverage script to the Pages `paths` filter (`.github/workflows/pages.yml:3-13`), which omits them today, so touching the generator currently deploys nothing. The wiki shows "generated N days ago"; the panel moves into `article-foot` on narrow viewports (residual part-g item).
- **Docs truth-up:** rewrite stale paragraphs (README, getting-started, roadmap, index.html hero/meta) against shipped behavior per CHANGELOG; drop test counts + JAR SHA from prose in favor of the generated stats surface; resolve the egg-catalog restart-vs-hot-read contradiction by reading the hatch code path and fixing the loser; state multi-pet status precisely (N active supported, formation UI not); MythicLib buffs listed as Shipped with a scope note (`/pet admin stats` + `OwnerBuffDiagnostics` prove application; the roadmap's "Deferred" is wrong). New this release and needing doc pages: supported Paper range (1.21.x, per binding decision), `/pet version`, `/pet admin diagnose`, offline `admin stats` + its new `omnipet.admin.stats` permission, `gui.studio.sessionTimeout`, the placed-egg CLAIM action and READY-break semantics, and a privacy paragraph for bStats + update checker (on by default, how to disable, what is sent).

## Related Code Files

- Modify: `docs/wiki.js` (h3, table escape, scroll-spy offset from runtime header height), `docs/wiki.css`, `docs/wiki.html` (chrome strings → `ui` map, stats in article-foot), `docs/wiki-content.js` (commands page from JSON, config sections, reload rows, roadmap truth, `/pet <page>`, admin stats row, reducer args, cultivation perms — EN+VI)
- Modify: `docs/index.html` (meta/OG/favicon/version/language toggle/CHANGELOG link fix), `404.html` (create, hash redirect)
- Modify: `README.md`, `docs/getting-started.md`, `docs/roadmap.md`, `docs/configuration.md`, `docs/commands-and-permissions.md`, `docs/troubleshooting.md` (new diagnose/version commands from Phase 6), `config.yml` header comment
- Create: Gradle task generating `docs/wiki-commands.json` from `OmniPetCommandTree` + a JUnit assert-only drift test in `omnipet-paper`, `docs/wiki-commands.json` (generated, committed), `docs/migration.md` (from Phase 2's migration contract: the one-way changes, the pre-upgrade backup step, and the no-supported-downgrade statement), config-coverage check in `tools/` (extend `verify-wiki-render.mjs` or sibling script, kebab-case name)
- Modify: `tools/verify-wiki-render.mjs` (5 new checks), `tools/verify-wiki-browser.mjs` (axe-core, tab traversal, 375px landing, stats panel), `.github/workflows/pages.yml` (Playwright cache, stats consumption), `.github/workflows/build.yml` (stats generation)
- Modify: `docs/wiki-stats.json` (regenerate; then CI-owned)

## Implementation Steps

1. Renderer fixes (h3 + table escape) + verify-script assertions; re-render check green locally from `file://`.
2. Commands JSON pipeline: Gradle generator task writing into the root `docs/`, JUnit assert-only drift test, `wiki-content.js` consumption, CI wiring.
3. Config coverage script + add missing sections/rows EN+VI.
4. Stale-claim rewrite across README/getting-started/roadmap/index.html; resolve egg-catalog contradiction from code; multi-pet precision; MythicLib buffs → Shipped with scope note.
4b. New-surface docs: Paper support stated as **two separate facts** — the compiled-against range (the full CI matrix, kept by operator decision on 2026-09-04: Paper 1.21 → 26.2) and the certified range (Phase 9's evidence; both vendors confirmed available, so expect real numbers rather than a waiver) — plus `/pet version`, `/pet admin diagnose`, offline stats + `omnipet.admin.stats`, studio timeout key, placed-egg CLAIM/READY-break, telemetry privacy paragraph.
4c. Write `docs/migration.md` from Phase 2's migration contract, but **reconcile it first** against what Phases 3 and 6 actually shipped — the contract was authored before their work landed, so verify the legacy egg-nonce rewrite and the new config keys match reality before publishing the list.
5. Chrome i18n (and persist the language choice in `localStorage` so it survives page changes) + stats placement + scroll-spy offset + landing meta/OG/favicon/404/version single-source + Google Fonts self-host + `prefers-color-scheme` support + a print stylesheet + VI copy for the landing hero (an EN-only hero behind a language toggle is worse than no toggle).
6. CI: implement the `workflow_run` stats handoff (see Architecture), add the missing `paths` entries, Playwright cache, and a `timeout-minutes` (about 15) on the deploy step so a hung Pages queue fails fast and can be re-run. After the first Phase 8 push, confirm the live site serves that commit (`wiki-stats.json` and the version string).
7. Browser verify extensions (axe, keyboard, mobile landing).
8. Full verify suite + Playwright run green; spot-check VI pages.

## Todo

- [ ] h3 + table-escape renderer fixes + verify coverage
- [ ] wiki-commands.json generated by Gradle, drift asserted by a write-nothing test, page consumes it
- [ ] `docs/migration.md` written from Phase 2's contract, reconciled against what Phases 3 and 6 shipped
- [ ] Config-key coverage check + missing sections added EN+VI
- [ ] Stale-claim cluster rewritten (README, getting-started, roadmap, landing)
- [ ] New surfaces documented (compiled-vs-certified Paper range, version/diagnose/stats commands, studio timeout, egg CLAIM, telemetry privacy)
- [ ] Egg-catalog restart-vs-hot contradiction resolved from code
- [ ] Chrome strings localized + language choice persisted; stats reachable on mobile; scroll-spy fixed
- [ ] Landing: meta/OG/favicon/404/version/language toggle + VI hero copy; fonts self-hosted; dark-scheme + print styles
- [ ] CI stats pipeline + Playwright cache + deploy-step timeout; live site verified against the pushed commit
- [ ] axe + keyboard checks in browser verify
- [ ] All verify scripts green

## Success Criteria

- [ ] Commands and config pages provably match code (Gradle-generated JSON + assert-only drift test + coverage script in CI)
- [ ] Zero stale shipped/not-shipped claims across README, docs, wiki, landing
- [ ] Renderer handles h3 and escaped pipes; verify scripts would catch both regressions
- [ ] Wiki fully bilingual including chrome; stats visible on mobile; axe pass clean
- [ ] `gradlew test` leaves the working tree clean (no generated file written by a test)
- [ ] An operator upgrading from 2.x has a documented backup + migration path
- [ ] Paper support reads as two distinct facts (compiled-against vs certified), not one blurred claim
- [ ] `file://` open still renders every page

## Risk Assessment

- Generated commands JSON must not break `file://` (no fetch) — commit the generated file and load it as a script global, same pattern as `wiki-content.js`. Signal: blank commands page from `file://`. Response: inline the JSON into `wiki-content.js` at generation time instead.
- The generated-artifact pipeline replaces a hand-synced table with a machine-synced one, but only if the drift check fails loudly. If the check can be skipped or warns silently, the commands page decays exactly the way the hand-synced version did — make the CI job a hard failure.
- h3 support changes TOC behavior part-g deliberately scoped out — keep h3 out of the TOC to avoid churn, note in verify.
- CI-committed stats can loop the pages workflow — generate into the deploy artifact, not a commit, or guard with `[skip ci]`.
