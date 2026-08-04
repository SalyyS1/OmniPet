---
title: "OmniPet UI coverage, command split, and bilingual wiki"
description: "Three operator-facing programmes: give every practical feature an inventory GUI, split the 653-line dispatcher into routers plus a dedicated /petadmin command, and publish a bilingual EN/VI documentation site with generated diagrams."
status: in-progress
priority: P1
effort: "7-11d"
tags: [gui, commands, docs, i18n]
created: 2026-08-04
blockedBy: []
blocks: []
---

# OmniPet UI coverage, command split, and bilingual wiki

## Overview

Three independent programmes. They share no files, so any one can stall without blocking the others.

| # | Ask | Evidence of the gap |
| --- | --- | --- |
| 1 | UI-ify the features | 28 of 37 command-tree nodes are chat-only. Two of them (`hatch use-main`/`use-off`) are holes in a menu that is *already open*: `HatchInventoryHolder.Type` has no redeem member (`:60`). |
| 2 | Split the command files | `OmniPetCommand.java` is **653 lines** against a documented 200-line limit, and every `/pet admin` branch is an inline `if` in one `execute` method (`:352-550`). |
| 3 | Bilingual wiki | `docs/` is English-only, `index.html` is one hand-written page whose Checkpoint card still claims "54 suites / 203 tests" against an actual 771. |

## Goals

| # | Goal | Priority |
| --- | --- | --- |
| 1 | Every feature worth a GUI has one; commands remain as the scriptable path | P1 |
| 2 | `/petadmin` exists as its own command, with `/pet admin` kept as an alias | P1 |
| 3 | No command source file over 200 lines | P2 |
| 4 | A bilingual EN/VI site with generated diagrams, deployable to GitHub Pages | P1 |

## Phases

| # | Phase | Status | Depends on | Why this order |
| --- | --- | --- | --- | --- |
| 1 | [Command split](./phase-01-command-split.md) | Pending | — | Everything in Phase 2 adds dispatch branches; splitting first means adding to small routers rather than a 653-line method. |
| 2 | [UI coverage](./phase-02-ui-coverage.md) | Pending | 1 | The new admin GUIs need somewhere to live that is not the monolith. |
| 3 | [Bilingual wiki](./phase-03-bilingual-wiki.md) | Pending | 1, 2 | Documents the finished surface, so it goes last and cannot describe something that then changes. |

## What gets a GUI, and what deliberately does not

From the coverage inventory. GUI-ing everything would be wrong: some commands exist precisely because they are scriptable or work on offline players.

**Gets a GUI (this plan):**
- `hatch use-main` / `use-off` — a hole in an open menu. Only input is which hand.
- An **admin hub** (`/petadmin`) → pending-work lists: `cultivation pending`/`review`, `skill pending`, `release list`, `transactions`. Each row already comes from a bounded in-memory list.
- Row click-through to a **confirm screen** for the money and item decisions: `reconcile`, `release recover`/`reconcile`, `cultivation recover`, `skill rollback`. The UUIDs these need are *already on the row* — clicking is what removes the typing.
- `item candy`/`breakthrough`/`instant`, `egg give`, `pet give` — a player picker plus an amount stepper.
- `/pet skill` — a picker over the caster's own vault and that pet's bindings.

**Stays command-only, on purpose:**
- `hatch reduce|set|complete|cancel` — designed for **offline** players (`HatchAdminController:19`), and `<millis>` is arbitrary. A GUI can only offer the online roster, so it would silently drop the case these exist for.
- `transactions <cursor>` — the cursor is an opaque versioned token printed for verbatim re-paste. A GUI holds it in holder state, but the command form is the only way to resume a scan from a ticket or a log.
- `egg create` — authoring; `egg-id` is a new identifier with no list, and `duration` is free text.
- `item reducer` — arbitrary seconds, bulk distribution.
- `admin reload` — console-first.
- The `pending`/`review` listers keep their command form: their output gets pasted into bug reports.

## Key constraints

- `omnipet-core` gains no Bukkit/Paper/Adventure imports. `checkCoreBoundary` enforces it.
- **No new bundled dependency.** `checkDistributionArtifact` must stay green.
- Existing `/pet admin ...` invocations must keep working. `/petadmin` is additive; scripts and docs in the wild do not break.
- Every new GUI placement binds its action and item in **one** call, as `MenuLayout.put` and `PetManagementMenuRenderer.put` already do. A drawn slot with no action is the dead-Close bug and must stay impossible.
- Any money or item decision needs a confirm screen that **re-reads** authoritative state at confirm time, as `PlayerSlotPurchaseController:196-200` does for price.
- Admin GUIs are read-mostly. Repository work stays off the main thread; Bukkit work stays on it.
- No source file over 200 lines. `OmniPetCommand` (653) and `PetStudioController` (639) both breach it today; Phase 1 fixes the first.

## Accepted contract changes

1. A new root command `/petadmin` with alias `/opadmin`, declared in `paper-plugin.yml`. `/pet admin ...` continues to work.
2. `OmniPetCommand` is split into per-area routers. Behaviour identical; `OmniPetCommandDispatchTest` must pass unmodified, which is the check that the split changed nothing.
3. `ChatInputService` is currently keyed on `StudioViewToken` (`ChatInputService:22`). If an admin GUI needs free text, that coupling gets generalised — otherwise admin GUIs avoid free text entirely and the class is untouched. **Preference: avoid free text**, so this stays a non-change.
4. `docs/` gains a second language. Content is authored EN-first with a VI translation beside it; the site remembers the choice.

## Success Criteria

- [ ] `gradlew.bat clean build` passes; four guard tasks green; no test skipped.
- [ ] No file in `paper/command/` exceeds 200 lines.
- [ ] `/petadmin transactions` and `/pet admin transactions` behave identically.
- [ ] `OmniPetCommandDispatchTest` passes with no edits, proving the split is behaviour-preserving.
- [ ] The hatch menu has working redeem buttons for both hands.
- [ ] `/petadmin` opens an admin hub whose tiles list pending work, and a row click reaches a confirm screen.
- [ ] Every money/item confirm re-reads state at confirm and refuses on a change.
- [ ] The site serves EN and VI, remembers the language, and works from `file://` as well as Pages.
- [ ] Diagrams are generated from repo sources, not pasted binaries.
- [ ] The Checkpoint card's test count is generated, not hand-typed, so it cannot go stale again.

## Risks

| Risk | Mitigation |
| --- | --- |
| The split silently changes dispatch order or permission checks | `OmniPetCommandDispatchTest` must pass **unmodified**; the split is mechanical extraction, not a rewrite. |
| `/petadmin` and `/pet admin` diverge over time | Both route into the same router objects; the tree stays the single source for help and tab-complete. |
| A new admin GUI fires an irreversible money action on a stale row | Confirm screen re-reads authoritative state; holder carries the expected revision, as the release confirmation already does. |
| An admin GUI does repository I/O on the main thread | Admin lists load through the existing async paths; only rendering happens on the main thread. |
| Bilingual docs drift, VI lagging EN | One shared page structure with per-language content files, so a missing VI string is visibly missing rather than silently English. |
| AI-generated banner unavailable | `GEMINI_API_KEY` is present in the environment, but the diagrams that carry meaning are SVG generated from repo data. If image generation fails, the site is still complete — banners are decoration. |
| Wiki claims something the code does not do | Every feature page cites the config key or command it documents; the test count is generated from build output. |

## Out of scope

Riding, Active Party, rename control, MMOItems identities, a live MythicMobs skill picker in the Studio, admin target mode, and live-server certification. Studio text localisation stays out unless Phase 3 finds it cheap: `MessageKey` deliberately keeps operator audit text in Java.

<!-- slug: omnipet-ui-coverage-command-split-bilingual-wiki -->
