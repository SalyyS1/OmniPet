---
phase: 5
title: "GUI lore polish"
status: complete
priority: P2
effort: "1d"
dependencies: [2, 4]
---

# Phase 5: GUI lore polish

<!-- Updated: Validation Session 1 - absorbed GUI title/lore catalog migration from Phase 2; vault lore uses safe raw component reads, not PetProgressionProjection -->

## Overview

Make every menu look deliberate: non-italic lore, consistent color grammar, grouped sections, real values instead of truncated UUIDs, and titles that say where you are. This phase also performs the **GUI half of the catalog migration** (titles + lore), so each renderer is edited exactly once.

## Requirements

- Functional: all five renderers use `GuiItems`; all names/lore/titles resolve through `MessageCatalog`; lore is grouped rather than a flat gray list.
- Non-functional: no lore line renders italic; no renderer holds a private item builder; no renderer performs I/O or repository access.

## Architecture

Current state per renderer:

- `PlayerPetMenuRenderer` — every pet row shows `Instance: <first 18 chars of UUID>` (`:45`, `abbreviate` at `:92`), which is noise for players. Replace with pet name, level, and rarity (see "Vault pet lore" below).
- `PetManagementMenuRenderer` — `shortId` truncation in three places (`:44`, `:78`, `:94`); reward lore built from enum names (`:103-111`).
- `HatchMenuRenderer` — countdown already formatted well (`:99-110`); needs color and grouping only.
- `SlotPurchaseMenuRenderer` — raw `amount.provider()` enum and plain-string balances (`:57-59`, `:89-90`).
- `StudioInventoryRenderer` — admin-facing; keeps IDs and revisions (staff need them) but gains grouping and color.

### Vault pet lore

Target lore: custom name or definition ID, `Level <n>`, `Rarity <id>`, then the click hint.

Read the values **defensively from `PetInstance.rawComponents()`**, not through the projections:

- `PetProgressionProjection.read` (`core/progression/PetProgressionProjection.java:14`) requires `initialStamina` and `nowEpochMillis`, and throws `IllegalArgumentException` when the component map is malformed or a field is non-numeric (`:19`, `:48`, `:58`). A renderer must never throw — one bad legacy pet would blank the whole vault page.
- Level lives at `rawComponents.progression.level`; rarity at `rawComponents.hatching.rarityId` (written by `IncubationPetFactory:20`). Legacy or migrated pets may have neither.

Add `paper/gui/player/VaultPetSummary.java`: a null-safe reader returning `Optional<Integer> level()` and `Optional<String> rarity()`, catching nothing because it tests types instead of casting. Missing level → omit the line. Missing rarity → omit the line. Never partially render an error string into lore.

Color grammar (one table in the plan, applied everywhere):

| Role | Color |
| --- | --- |
| Item title, neutral | `#FFD700` gold |
| Positive / active / affordable | green |
| Warning / pending / overflow | yellow |
| Blocked / locked / unaffordable | red |
| Static descriptive lore | gray |
| Dynamic values inside lore | white |
| Section heading inside lore | dark gray |

Lore shape: value lines first, blank spacer, then the action hint last (`Left-click: ...`). Players read the bottom line for what to do, so it stays in a fixed position across every menu.

Titles gain location context: `OmniPet ▸ Vault (1/3)`, `OmniPet ▸ Hatch`, `OmniPet ▸ Manage ▸ <pet>`, `OmniPet Studio ▸ B tier`. Titles are catalog keys with `<page>`/`<pages>`/`<pet>` placeholders.

Enum-to-text: add `paper/text/Displays.java` with `provider(EconomyProvider)`, `status(Enum)`, `tier(PetTier)`, `modifier(StatModifierType)`. Replaces the scattered `name().toLowerCase().replace('_',' ')` calls (`PlayerPetController.java:242`, `IncubationActionItemController.java:132`, `OmniPetCommand.java:467`) so `ENTITLEMENT_SYNC_PENDING` reads as `Entitlement sync pending` in exactly one place.

## Related Code Files

- Create: `paper/text/Displays.java`, `paper/text/DisplaysTest.java`
- Create: `paper/gui/player/VaultPetSummary.java`, `paper/gui/player/VaultPetSummaryTest.java`
- Modify: `gui/player/PlayerPetMenuRenderer.java`, `gui/player/PetManagementMenuRenderer.java`, `gui/player/SlotPurchaseMenuRenderer.java`, `gui/hatch/HatchMenuRenderer.java`, `studio/bukkit/StudioInventoryRenderer.java` (+ `StudioStatScreens.java` from Phase 4)
- Modify: `text/MessageKey.java` — `gui.*` title and lore keys (the GUI half of the catalog, deferred here from Phase 2)
- Modify: `OmniPetCommand.java:467` — reject-reason wording via `Displays.status`
- Delete: the five private `item(...)` helpers and both `abbreviate`/`shortId` helpers

## Implementation Steps

1. Add `Displays` with the enum-to-text conversions; test each mapping including a value containing underscores.
2. Add `VaultPetSummary` with type-tested reads of `progression.level` and `hatching.rarityId`; test a full pet, a pet with no progression component, a pet with a non-numeric level, and a legacy pet with neither — none may throw.
3. Add the `gui.*` keys to `MessageKey` (titles and lore for all five renderers).
4. Migrate `HatchMenuRenderer` first — it is the smallest and has the stack-preserving overload that will prove `GuiItems.of(ItemStack, ...)` keeps skull meta.
5. Migrate the remaining four renderers to `GuiItems` + catalog keys; delete the private builders and truncation helpers as each is emptied.
6. Apply the color grammar and the value-then-spacer-then-hint lore shape.
7. Replace player-facing UUID fragments with meaningful values; keep IDs/revisions in the admin Studio and management screens where staff need them.
8. Add titles with location context and page placeholders.
9. Extend the Phase 1 `GuiItemsTest` into a renderer contract test: for each renderer's produced items, assert no display name or lore line has `ITALIC` unset or true.

## Success Criteria

- [x] No private `item(...)` builder remains in any renderer.
- [x] Zero lore lines render italic (asserted per renderer).
- [x] Vault rows show name + level + rarity where present, and degrade cleanly when absent; no truncated UUID in player lore.
- [x] A pet with a malformed progression component still renders (asserted).
- [x] Enum text appears human-readable everywhere and comes from `Displays`.
- [x] Titles include location and page context.
- [x] Every string in the five renderers resolves through `MessageCatalog`.
- [x] Existing GUI/click tests pass with one updated assertion; holder/action maps untouched.

Report: [`plans/reports/phase-05-gui-lore-polish-report.md`](../reports/phase-05-gui-lore-polish-report.md).
145 suites / 586 tests (+22). 51 `gui.*` keys plus 3 `manage.*` keys added.

Additions beyond the plan:

- `gui/GuiColors.java` holds the colour grammar as constants, so renderers select a role rather than
  repeating hex values. The plan specified the table but not where it lived.
- `gui/player/SlotBalanceDisplay.java` — the controller was building the balance display string
  itself, which left GUI text outside the catalog. A typed value moves the wording to the renderer
  while the controller keeps the provider I/O.
- `Layout.title` and `Entry.name`/`lore` moved `String` → `Component`. Unavoidable for catalog
  ownership; one `PetManagementMenuContractTest` assertion updated, three unchanged.

Note recorded while testing: a **partial** progression component makes `PetManagementViewModel` throw,
because management legitimately uses the projection. Absent-entirely is safe. The vault's tolerance
for partial data is `VaultPetSummary`'s responsibility and is tested there.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Editing renderers disturbs slot→action maps | Only item appearance changes; `actions.put` lines stay byte-identical. Click tests must pass without edits. |
| Removing UUID lore hides info admins rely on | Full UUIDs stay in the management screen and Studio, and in admin command output. |
| Component reads throw on a malformed legacy pet | `VaultPetSummary` type-tests instead of casting and omits missing lines; explicitly tested with malformed input. Projections are deliberately not used in render paths. |
