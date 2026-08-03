---
title: "OmniPet UX overhaul: help, tab-complete, message catalog, Studio input, player hub"
description: "Fix discoverability and authoring UX: /pet help, Brigadier suggestions, messages.yml text catalog, Studio stat/head input that explains itself, non-italic structured lore, and a unified player hub."
status: complete
priority: P1
effort: "3-5d"
tags: [ux, paper, studio, i18n, gui]
created: 2026-08-03
blockedBy: []
blocks: []
---

# OmniPet UX overhaul: help, tab-complete, message catalog, Studio input, player hub

## Overview

Every reported problem is a presentation/UX defect, not a durability defect. The persistence, escrow, reservation, and reconciliation layers stay untouched. Root causes found in code:

| Report | Verified root cause |
| --- | --- |
| No tab-complete | `OmniPetCommand` implements `BasicCommand` but never overrides `suggest(CommandSourceStack, String[])` (`command/OmniPetCommand.java:25`). Paper therefore has nothing to offer. |
| No help command | No `help` branch exists; usage strings are duplicated inline across 8 failure paths. |
| Looks bare | 5 renderers each duplicate a private `item(...)` helper; **zero** `TextDecoration.ITALIC` calls exist repo-wide, so all lore renders vanilla italic; lore is flat gray `label: value` with no grouping. |
| Stat picker "all paper", no material/lore split | `StudioInventoryRenderer.stats` hardcodes `Material.PAPER`/`LIME_DYE` for every entry and dumps `Set.toString()` of modifiers (`studio/bukkit/StudioInventoryRenderer.java:158-162`). |
| "Forced to type modifier, nothing works" | `PetStudioController.awaitField` closes the inventory and awaits chat **without sending any prompt** (`studio/bukkit/PetStudioController.java:400-415`). The player is given no format, no example, no cancel hint. `catalogStat` then hard-requires exactly 3 tokens. |
| Head icon should accept raw base64 | `StudioDraftInputParsers.icon` requires two tokens `<SOURCE> <value>` (`studio/bukkit/StudioDraftInputParsers.java:27-35`). A pasted base64 blob is one token → rejected. Core validation already accepts that exact payload shape. |

Decisions locked with the user: text moves to `messages.yml` (English content, MiniMessage), scope includes the unified player hub, stat modifier gets a click screen **and** chat still accepts the full 3-token form, head input auto-detects format while keeping the legacy 2-token syntax.

## Goals

| # | Goal | Priority |
| --- | --- | --- |
| 1 | `/pet help` + permission-filtered Brigadier suggestions from one shared command spec | P1 |
| 2 | Every prompt states format, example, and cancel — no silent chat capture | P1 |
| 3 | Stat picker shows per-stat material + structured lore; modifier chosen by click | P1 |
| 4 | Head icon accepts pasted base64, texture URL, or bare texture hash | P1 |
| 5 | `messages.yml` owns all chat text, GUI titles, and GUI lore; missing keys fall back to built-in defaults | P1 |
| 6 | Non-italic, grouped, colored lore through one shared item builder | P2 |
| 7 | `/pet` opens a hub; `/pet vault [page]` and `/pet <page>` keep vault access | P2 |

## Phases

| # | Phase | Status | Depends on |
| --- | --- | --- | --- |
| 1 | [Phase 1: Start](./phase-01-start.md) | Complete | — |
| 2 | [Phase 2: Message catalog foundation](./phase-02-message-catalog-foundation.md) | Complete | 1 |
| 3 | [Phase 3: Command help and tab-complete](./phase-03-command-help-and-tab-complete.md) | Complete | 2 |
| 4 | [Phase 4: Studio input UX](./phase-04-studio-input-ux.md) | Complete | 2 |
| 5 | [Phase 5: GUI lore polish](./phase-05-gui-lore-polish.md) | Complete | 2, 4 |
| 6 | [Phase 6: Player hub](./phase-06-player-hub.md) | Complete | 3, 5 |
| 7 | [Phase 7: Docs and verification](./phase-07-docs-and-verification.md) | Complete | 3, 4, 5, 6 |

**All seven phases complete.** Final build: 18 tasks, 147 suites / 605 tests, zero failures/errors/skips,
JAR 1,674,717 bytes, SHA-256 `485AC4C6…4299A9`, zero Maven or bundled MiniMessage entries. Baseline was
132 suites / 461 tests.

## Key constraints

- `omnipet-core` must not gain Bukkit/Paper/Adventure imports. `checkCoreBoundary` (`build.gradle.kts:17`) enforces `org.bukkit.`/`io.papermc.`/vendor prefixes, and core's lockfile carries only SnakeYAML. All new UI/text code lands in `omnipet-paper`.
- MiniMessage is verified available: `net.kyori:adventure-text-minimessage:4.17.0` on `compileClasspath` via `compileOnly(libs.paper.api)` (`omnipet-paper/gradle.lockfile`), supplied by Paper at runtime. `MiniMessage.miniMessage()` and `Placeholder.unparsed/parsed` confirmed present in the cached jar. Nothing new is bundled — `checkDistributionArtifact` stays green.
- All player-supplied strings (pet custom name, definition ID, player name, provider diagnostics) go through `Placeholder.unparsed` so a crafted name cannot inject `<click:run_command>` or color tags into another viewer's UI.
- No change to escrow, journal, reservation, revision, generation, or queue semantics. New reads reuse `PerPlayerTaskQueue` with their own namespaced keys; existing `vault:view` / `slot:view` coalescing rules are unchanged.
- Files over 200 lines get split rather than grown. `OmniPetCommand` (556 lines, 10 overloads) is **not** rewritten: help/suggest logic lands in new sibling classes it delegates to.

## Accepted contract changes

1. `/pet` with no arguments opens the hub instead of vault page 1. `AdminPetCommandParser.parse` gains an `OpenHub` result; `AdminPetCommandParserTest.preservesOneBasedPlayerPageArguments` must be updated in the same commit. `/pet <page>` and the new `/pet vault [page]` preserve vault access.
2. `messages.yml` is a new operator-owned file. Unknown keys warn and are ignored; missing keys use built-in defaults. It never fails plugin startup.

## Success Criteria

- [ ] `gradlew.bat clean build --no-daemon --console=plain` passes with zero failures/skips; new suites cover the spec, parsers, and catalog.
- [ ] Typing `/pet ` then Tab offers only branches the sender may use; `/pet admin ` hides nodes whose permission is absent.
- [ ] `/pet help` prints a paged, permission-filtered command list built from the same spec that drives suggestions.
- [ ] Pasting the user's raw base64 (`eyJ0ZXh0dXJlcyI6...`) into the head field is accepted and previews the head in the editor slot.
- [ ] Selecting a MythicLib stat opens a modifier screen with three explained choices; the follow-up chat prompt names format, example, and `cancel`; both `10 50` and `FLAT 10 50` are accepted.
- [ ] Stat entries render distinct materials with grouped lore (display name, ID, provider, human-readable modifiers, current selection).
- [ ] No lore line renders italic anywhere in the plugin.
- [ ] `/pet` opens the hub; hub tiles route to vault, hatch, slot purchase, help, and Studio (admin only); every GUI exposes a back-to-hub control.
- [ ] Deleting `messages.yml` regenerates it; corrupting one value logs a warning and uses the default without disabling the plugin.

## Validation Log

### Session 1 — 2026-08-03
**Verification pass (Full tier, 7 phases).** Claims checked: 22. Verified: 21. Failed: 1. Unverified: 0.

| Claim | Result | Evidence |
| --- | --- | --- |
| `BasicCommand.suggest` exists as a default method to override | VERIFIED | `javap` on paper-api 1.21: `default Collection<String> suggest(CommandSourceStack, String[])` |
| `Commands#register(String, Collection, BasicCommand)` wires suggestions | VERIFIED | `javap` on `io.papermc.paper.command.brigadier.Commands` |
| MiniMessage available at compile time, provided at runtime | VERIFIED | `omnipet-paper/gradle.lockfile` → `net.kyori:adventure-text-minimessage:4.17.0=compileClasspath`; `MiniMessage.miniMessage()` and `Placeholder.unparsed` present in the cached jar |
| Zero `TextDecoration.ITALIC` calls repo-wide | VERIFIED | grep over `omnipet-paper/src/main` returns nothing |
| Five renderers each hold a private `item(...)` | VERIFIED | `PlayerPetMenuRenderer:81`, `PetManagementMenuRenderer:126`, `SlotPurchaseMenuRenderer:117`, `HatchMenuRenderer:85`, `StudioInventoryRenderer:229` |
| `awaitField` sends no prompt before capturing chat | VERIFIED | `PetStudioController.java:400-415` |
| `icon(String)` rejects a single-token base64 paste | VERIFIED | `StudioDraftInputParsers.java:27-35` |
| Core already validates the user's base64 payload shape | VERIFIED | `PetDefinitionStudioService.java:327-353` requires `textures.SKIN.url`, absolute HTTP(S), ≤16 KiB |
| Stat rows hardcode `PAPER`/`LIME_DYE` and print `Set.toString()` | VERIFIED | `StudioInventoryRenderer.java:158-162` |
| Every admin permission in the planned tree exists | VERIFIED | cross-checked against `paper-plugin.yml:64-73` and each parser's constant |
| `submitLatest` coalescing is per-key and namespaced | VERIFIED | `PerPlayerTaskQueue.java:68`, `:158-166` |
| `checkCoreBoundary` blocks Bukkit/Paper/vendor imports in core | VERIFIED | `build.gradle.kts:17-36` |
| `AdminPetCommandParserTest` asserts no-args → `PlayerPage(1)` | VERIFIED | `AdminPetCommandParserTest.java:47,50` — must change with the hub |
| Free GUI slots for back-to-hub (vault 48, hatch 18, manage 27) | VERIFIED | slot maps at `PlayerPetMenuRenderer:50-72`, `HatchMenuRenderer:45-75`, `PetManagementMenuRenderer:40-79` |
| Hub can read pets + incubation in one call | VERIFIED | `RepositoryHatchService.snapshot` returns `PlayerState`, which carries both (`PlayerState.java:11-21`) |
| Phase 5 could read level via `PetProgressionProjection.read` | **FAILED** | It requires `initialStamina`/`nowEpochMillis` and throws on malformed components (`PetProgressionProjection.java:14,19,48,58`). A renderer must not throw. **Corrected:** Phase 5 adds `VaultPetSummary`, a null-safe raw-component reader. |

**Interview decisions (10 questions across 2 rounds):**

1. **Text handling** — extract to `messages.yml`, keep English content. Not a Vietnamese default; not in-place-only edits.
2. **Scope** — polish plus the unified player hub, not polish alone.
3. **Stat input** — click-to-choose modifier **and** chat still accepts the full `FLAT 10 50` form.
4. **Head input** — auto-detect format, keep the legacy `<SOURCE> <value>` syntax working.
5. **Hub entry** — `/pet` opens the hub; vault moves to `/pet vault [page]` with `/pet <page>` retained.
6. **Tab-complete depth** — static keywords with permission filtering only. No UUID enumeration, no disk scans.
7. **Stat icons** — semantic keyword→icon mapping (`attack`→sword, `defen`→shield, …) with `PAPER` fallback, not a hash palette and not one shared icon.
8. **Vault lore** — pet name + level + rarity, read safely from raw components.
9. **Phase 2 size** — split: chat migration in Phase 2, GUI titles/lore folded into Phase 5 so each renderer is edited once.
10. **Admin output** — stays hardcoded. `messages.yml` covers player-facing text only; transaction pages, cursors, and reconcile reasons remain an audit trail in Java.

**Propagation:**

- Phase 1 — worklist entries now classified `PLAYER` / `OPERATOR`.
- Phase 2 — scope narrowed to catalog infra + 5 player-facing chat controllers; `admin.*` keys dropped; GUI migration moved out.
- Phase 3 — `vault [page]` removed from the initial tree; Phase 6 adds it with its dispatcher branch so help never advertises a dead command.
- Phase 4 — added `StatMaterialPalette` with the keyword table and its test.
- Phase 5 — now depends on Phase 4; absorbed GUI catalog migration; added `VaultPetSummary` to replace the rejected projection read.
- Phase 6 — hub read documented as one `PlayerState` call reused via `PetStorageSnapshot.from`; coalescing evidence cited to exact lines.

### Whole-Plan Consistency Sweep

Re-read `plan.md` and all seven phase files after propagation.

- Dependency graph consistent: `plan.md` phase table, phase frontmatter `dependencies`, and the task-tool `blockedBy` chain all read 1 → 2 → {3, 4} → 5 → 6 → 7 with 5 depending on both 2 and 4.
- `vault [page]` now appears only in Phase 6 (creation) and Phase 7 (docs). Phase 3 states the omission and its reason.
- GUI text migration appears only in Phase 5. Phase 2's file list explicitly names the untouched operator files.
- `messages.yml` is described identically in Phases 2, 6, and 7: player-facing only, lenient, never fatal.
- Head-icon rules stated once in Phase 4 and referenced (not restated) by Phase 7's docs table.
- The `/pet` contract change is stated in `plan.md` "Accepted contract changes", Phase 6, and Phase 7's CHANGELOG row — identical wording.
- No unresolved contradictions.

## Risks

| Risk | Mitigation |
| --- | --- |
| Message extraction touches many files and can silently change assertions | Keep every default string byte-identical to today's text; wording changes only in Phase 5, after tests are green. Scope split (chat in Phase 2, GUI in Phase 5) keeps each commit small. |
| MiniMessage tag injection from user data | `Placeholder.unparsed` for all dynamic values; a test asserts a `<red>`-laden pet name renders literally. |
| Hub becomes a new I/O path | Hub does exactly one coalesced `PlayerState` read under key `hub:view`; no new repository or journal API. |
| `/pet` behavior change surprises players | `/pet <page>` and `/pet vault` retained; hub tile 1 is Vault; release note in CHANGELOG. |
| Studio screen/token churn breaks stale-view protection | New screens reuse `StudioViewToken`/`nextView` exactly as existing screens do; stale-modifier-screen test required. |
| A renderer throws on a malformed legacy pet | `VaultPetSummary` type-tests raw components and omits missing lines; projections are never called from render paths. |

<!-- slug: omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub -->
