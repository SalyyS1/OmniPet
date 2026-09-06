# Wiki / docs / landing audit (scout-wiki2 -> planner)

Read-only pass over `docs/` (wiki.html/js/css, wiki-content.js, index.html, all md manuals), `.github/workflows/pages.yml`, `tools/verify-wiki-*.mjs` (2026-09-03). Bash returned empty output all session, so renderer defects and parity results come from code reading, not execution. Report persisted by planner from the scout's inline message.

## Summary
- Two renderer-level defects ship today: `\|` inside commands-table cells breaks column split (7 rows EN+VI), and `###` sub-headings on the config page render as literal text (8 occurrences). Both invisible to `verify-wiki-render.mjs`.
- Wiki commands page omits `/pet admin stats`, `/pet <page>`, reducer `<seconds>` arg, and cultivation perm on candy/breakthrough; config page omits ~9 config.yml sections and the reload table omits `render`/`idle-play`. `docs/commands-and-permissions.md` is actually more accurate than the wiki it defers to.
- Stale-claim cluster: README, getting-started.md, roadmap.md, paper-plugin.yml still say passive triggers / entity click / visible pets / skills are "not shipped"; three conflicting test counts (461 / 699 / 1087); wiki-stats.json dated 2026-08-06 and never CI-generated.
- Bilingual parity is good: all 12 pages have matching EN/VI `##`/`###` heading counts; only hardcoded English chrome strings in `wiki.html` leak into the VI view.
- Part-G Phase 13/14 items verified implemented except: build-stats panel unreachable on mobile, `###` limitation now actively triggered, `file://` stats dash.

## Findings

| Sev | file:line | What | Why it matters | Suggested fix |
|---|---|---|---|---|
| High | `docs/wiki-content.js:428,446,450-452` + VI `:474,492,496-498` | Table cells contain `\|` (e.g. `/pet hatch [main-or-off-or-claim...]`); `renderTable` (`docs/wiki.js:247`) does `split('pipe')` with no escape | Hatch, reduce/set, complete/cancel, reconcile rows render with wrong column count on the reference page | Rewrite cells with `/` or `{a,b}`; or add escape support to `renderTable` + column-count assert in `verify-wiki-render.mjs` |
| High | `docs/wiki-content.js:726,757,776,794` + VI `:1045,1076,1095,1113` | `### Triggers/Targeting/Everything else/Cooldown feedback` — `renderMarkdown` (`wiki.js:179`) only handles `## ` | Skills section shows literal "### Triggers" paragraphs; not in TOC, not deep-linkable | Promote to `## ` or add `h3` support; add `###` rejection to render verify |
| High | `README.md:3,47` | "Riding, entity click interaction, and live-server certification remain phased work"; deferred list names "passive event triggers" | Entity click shipped (2a03ce0); 7 passive triggers in `SkillTrigger.java` (9bf8629). First-read surface contradicts wiki "seventeen triggers... Shipped" (`wiki-content.js:116`) | Rewrite to: riding + live certification only |
| Med | `docs/wiki-content.js:433-452` | Admin table lacks `/pet admin stats [player_name]` (`OmniPetCommandTree.java:82-84`, perm `omnipet.admin.reload`) | Shipped diagnostic absent from "reference"; `commands-and-permissions.md:40` has it | Add row EN+VI |
| Med | `docs/wiki-content.js:441` | `/petadmin item <type> <player> [amount]` generic | Reducer is `<player> <seconds> [amount]` (`tree:103`); candy/breakthrough need `+ omnipet.admin.cultivation` (`tree:110-113`) | Split into 2 rows or add args/perm note |
| Low | `docs/wiki-content.js:422-431` | No `/pet <page>` shortcut | Documented in `tree:11-12` and md:23 | Add to vault row |
| Med | `docs/wiki-content.js:509-1158` | Config page omits `storage.vault.legacyPermission`, `activeSlots.entitlement`, `idle-play`, `progression.{maxLevel,maxStamina,staminaRegenPerSecond,overflowPolicy}`, `gui.feedback`, `gui.onboarding.firstJoinGreeting`, `gui.locale`, `gui.studio.autoCreateEgg`, `render.maximumLeanDegrees` (all in `config.yml`) | Page claims reference status but `configuration.md` covers more; operator can't find keys by search | Add sections, or explicit "keys not covered" list; add key-coverage script |
| Med | `docs/wiki-content.js:589-593` / VI `:908-912` | Reload table omits `render.*` (RESTART ONLY + strict per `config.yml`) and `idle-play` | Operator edits nameplate/lean values, reloads, sees nothing | Add rows |
| Low | `config.yml` header | "Full reference: docs/configuration.md" while md banner says wiki wins | Circular authority | Point to wiki URL |
| Med | `paper-plugin.yml:5` | `description: "OmniPet greenfield foundation."` | Shown in `/plugins`, `/version`, plugin lists | Replace with product description |
| Med | `docs/wiki-content.js:115` vs `:1875` vs `config.yml activeSlots.multiPetEnabled/max 5` | Overview: "multiple pets at once — Not yet"; roadmap: "data layer supports it; no formation UI" | Reader can't tell whether 2+ active pets work | State precisely: N active pets supported, formation/party UI not |
| Med | `docs/wiki-content.js:1877` | "Owner stat buffs from MythicLib — Deferred" | `/pet admin stats` explains MythicLib stat application (`tree:82`, `OwnerBuffDiagnostics.java`, md:40) — appears shipped | Move to Shipped or clarify scope |
| Med | `docs/configuration.md:13` vs `docs/getting-started.md:84` | "eggs ... restart required after edits" vs "hatch reads catalog from disk on each use" | Direct contradiction on operator-facing behaviour | Verify against hatch code, fix loser |
| Med | `README.md:69`, `docs/getting-started.md:27,39`, `docs/wiki-stats.json` | Test counts 699 / 461 / 1087; JAR bytes+SHA in prose; stats generatedAt 2026-08-06 (210 `*Test.java` now) | Three numbers can't all be true; SHA rots per build | Drop counts/SHA from prose; single generated source |
| Med | `README.md:87` | "repository wiki... has no initial page yet, so repository docs remain authoritative" | Every md banner says opposite | Delete sentence, link Pages wiki |
| Med | `docs/getting-started.md:5,50` | "live renderers, pet skills/buffs, progression remain later phases"; "Visible pet runtime behavior is not shipped yet" | All shipped | Rewrite intro/scope para |
| Med | `docs/roadmap.md:5,53` | "later slices add feeding, bonding, temporary buffs, multi-pet formations"; "does not ship riding or passive event triggers" | Passive triggers shipped | Align with wiki roadmap page |
| Low | `docs/index.html:6,39` | Meta description "verified Paper foundation... slot recovery slice"; hero "schema-4 player vault" | Internal jargon in SEO/social preview | Plain product sentence |
| Low | `docs/index.html:66`, `wiki-content.js meta.version`, `build.gradle.kts` | Version string in 3 places | Bump miss on release | Single `version.json` or read stats file |
| Med | `docs/wiki.html:68-76` + `wiki.css:678` | Build-stats panel lives in `.wiki-aside`, `display:none` <1180px; dash on `file://` | Mobile/tablet never sees stats; still-open Part-G item | Render stats in article-foot on narrow; hide panel when fetch fails |
| Low | `docs/wiki.html` | "Skip to content", "Source", "Build", "Tests" hardcoded EN, not in `ui` strings | VI view shows mixed language | Add to `ui` and set in `renderChrome` |
| Low | `docs/wiki.js:607` `var line = 110` vs `wiki.css:319,753` scroll-margin 6rem / 1rem mobile | Scroll-spy offset differs from actual header height on mobile | Wrong TOC highlight after deep link | Read header `offsetHeight` at runtime |
| Low | `docs/styles.css`, `docs/wiki.css` | Google Fonts external | Third-party request, FOUT, offline/file:// fallback | Self-host woff2 + `font-display: swap` |
| Low | `docs/index.html` | No language switch; wiki is bilingual | VN visitors land in EN | Add toggle reusing `ui` strings |
| Low | `docs/index.html`, `docs/wiki.html` | No OG/Twitter meta, favicon, `404.html` | Share previews blank; deep links to wrong path 404 | Add both |
| Med | `.github/workflows/pages.yml` | Paths filter = `docs/**` + listed tools; `build-wiki-stats.mjs` never run in any workflow | Java command/config edit never re-verifies wiki; stats file only updates by hand | `build.yml` emits stats artifact -> pages job consumes; or `workflow_run` trigger |
| Low | `.github/workflows/pages.yml` | Playwright `--with-deps chromium` every run, no cache | Slow deploys | `actions/cache` on `~/.cache/ms-playwright` |
| Low | `tools/verify-wiki-render.mjs` | No `###` detect, no internal `#/page#heading` link resolution, no table column-count check, no EN/VI heading parity, no stats freshness | Would have caught both High renderer bugs | Add 5 checks (~60 lines) |
| Low | `tools/verify-wiki-browser.mjs` | No axe/contrast, no keyboard traversal, no `index.html` @375px, no language persistence, no stats panel on mobile | A11y regressions ship silently | Add axe-core + tab-order test |

Drift spot-check tally: 12 items current-state drift, 9 stale claims. Parity: heading counts match on all 12 pages (overview 2/2, install 4/4, player 8/8, commands 4/4, config 14/14, studio 4/4, troubleshooting 6/6, integrations 5/5, compat 4/4, migration 6/6, dev 6/6, roadmap 4/4).

## Upgrade opportunities
- Generate commands page data from `OmniPetCommandTree` (JUnit writes `docs/wiki-commands.json`; `wiki-content.js` renders it) — kills the whole drift class; `OmniPetCommandTreeContractTest` already proves perms.
- Config-key coverage script: parse `config.yml` leaf paths, assert each appears in wiki config page or an allow-list.
- Renderer: `h3`, table-cell escape, column-count assert, internal-link resolution.
- Stats pipeline owned by CI: `build.yml` -> artifact -> pages deploy; show "generated N days ago", hide panel >30 days.
- Move stats + version out of aside into `article-foot` on narrow viewports.
- Self-host fonts, `preload`, `font-display: swap`.
- OG/Twitter meta, favicon, `404.html` with hash redirect.
- Localize chrome strings; persist language choice.
- Single version source (`version.json` written by Gradle) consumed by index + wiki.
- Reduce md duplication: md files become short stubs + wiki link, or CI diff-check that md/wiki never both state test counts.
- Landing page language toggle + VI hero copy.
- `axe-core` pass + keyboard traversal in browser verify; `prefers-color-scheme` + print stylesheet.

## Unresolved questions
- Are MythicLib owner stat buffs shipped (README:39, `/pet admin stats`, `OwnerBuffDiagnostics.java` say yes; wiki roadmap says Deferred)?
- Does "multiple pets at once — Not yet" mean formation UI only, given `activeSlots.multiPetEnabled` + max 5?
- Egg catalog: restart required (`configuration.md:13`) or hot-read (`getting-started.md:84`)? Needs code check on hatch start.
- Should `docs/examples.md` / `docs/design-guidelines.md` get wiki pages or stay md-only?
- Is `wiki-stats.json` meant to be hand-regenerated per release or CI-owned?
- `index.html` footer CHANGELOG link: file exists at repo root, but Pages serves `docs/` only — href not verified, may 404.
