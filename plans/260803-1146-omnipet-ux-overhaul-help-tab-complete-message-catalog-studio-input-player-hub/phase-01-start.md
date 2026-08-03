---
phase: 1
title: "Start"
status: complete
priority: P1
effort: "2h"
dependencies: []
---

# Phase 1: Start

## Overview

Freeze the current text/behavior baseline and add the one shared helper that later phases depend on. No user-visible change ships in this phase.

## Requirements

- Functional: an inventory-item builder that produces non-italic lore and is usable by every renderer.
- Non-functional: no behavior change, no new dependency, `checkCoreBoundary` still green.

## Architecture

Today five renderers each hold a private `item(Material, String, NamedTextColor, String...)` copy:

- `gui/player/PlayerPetMenuRenderer.java:81`
- `gui/player/PetManagementMenuRenderer.java:126`
- `gui/player/SlotPurchaseMenuRenderer.java:117`
- `gui/hatch/HatchMenuRenderer.java:85` (two overloads)
- `studio/bukkit/StudioInventoryRenderer.java:229`

None disables italic, so every lore line renders italic in-game. That single omission is most of the "sơ sài" impression. Introduce `paper/gui/GuiItems` as the sole builder, then migrate call sites in Phase 5 once the message catalog exists.

Deliberately **not** in this phase: changing any visible string. Baseline capture first so Phase 2's extraction can be proven text-identical.

## Related Code Files

- Create: `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/gui/GuiItems.java`
- Create: `omnipet-paper/src/test/java/io/github/salyvn/omnipet/paper/gui/GuiItemsTest.java`
- Read only (baseline): the five renderers above

## Implementation Steps

1. Record the current build baseline: `gradlew.bat clean build --no-daemon --console=plain`, note suite/test counts. This is the comparison point for every later phase.
2. Grep and record every literal string a player or admin can see (`sendMessage`, `Component.text`, `createInventory` titles). 82 `sendMessage` call sites across 14 files; keep the list in `plans/reports/` as the extraction worklist for Phase 2. **Classify each line `PLAYER` or `OPERATOR`** — validated scope keeps operator/console output (transaction pages, cursors, reconcile reasons, offline inspect dumps) hardcoded, so only `PLAYER` lines become catalog keys. Where one class serves both, classify per call site, not per file.
3. Add `GuiItems` with:
   - `ItemStack of(Material, Component name, List<Component> lore)`
   - `ItemStack of(ItemStack base, Component name, List<Component> lore)` — preserves skull meta, replacing `HatchMenuRenderer`'s stack overload
   - `Component label(String text, NamedTextColor color)` returning `Component.text(...).decoration(TextDecoration.ITALIC, false)`
   - `ItemStack filler()` for the gray pane
   - Every produced display name and lore line has `ITALIC` explicitly false.
4. Add `GuiItemsTest` asserting `ITALIC` resolves to `FALSE` (not `NOT_SET`) on names and lore, and that passing a `PLAYER_HEAD` base stack keeps its meta type.

## Success Criteria

- [x] Baseline suite/test counts recorded in `plans/reports/`.
- [x] Visible-string worklist written to `plans/reports/`, every line classified `PLAYER` or `OPERATOR`.
- [x] `GuiItems` exists with italic explicitly disabled and is covered by tests.
- [x] Build result matches the recorded baseline (no renderer migrated yet).

Report: [`plans/reports/phase-01-baseline-and-visible-string-worklist-report.md`](../reports/phase-01-baseline-and-visible-string-worklist-report.md).
Baseline: 132 suites / 461 tests, JAR 1,589,478 bytes, SHA-256 `0DC987AE…B92386` — identical to `README.md:68`.

## Risk Assessment

Low. Additive only. Risk is forgetting a call site during the Phase 5 migration — the worklist file is the mitigation.
