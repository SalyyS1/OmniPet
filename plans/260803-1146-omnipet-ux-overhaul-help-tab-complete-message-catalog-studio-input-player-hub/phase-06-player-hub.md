---
phase: 6
title: "Player hub"
status: complete
priority: P2
effort: "1d"
dependencies: [3, 5]
---

# Phase 6: Player hub

## Overview

Replace four disconnected entry points with one hub. `/pet` opens the hub; `/pet vault [page]` and `/pet <page>` keep direct vault access. This is the deferred "combined player hub" from `docs/roadmap.md`, scoped to navigation only.

## Requirements

- Functional: hub shows Vault, Hatch, Active Slots, Help, and (admin only) Studio, each with live summary lore; every player GUI gains a back-to-hub control.
- Non-functional: one coalesced storage read per hub open under a new namespaced key; no new repository or journal API; no change to vault/hatch/slot mutation paths.
- Accepted contract change: `/pet` no longer opens vault page 1 directly.

## Architecture

New `paper/gui/hub/`:

- `HubInventoryHolder` — viewer, expected revision, slot→action map, following `PlayerPetInventoryHolder`'s shape exactly (`gui/player/PlayerPetInventoryHolder.java`).
- `HubMenuRenderer` — 27 slots built with `GuiItems` + catalog keys. Tiles:
  | Slot | Tile | Summary lore from |
  | --- | --- | --- |
  | 11 | Vault | `ownedCount`/`effectiveVaultCapacity`, desired-active count, overflow warning |
  | 13 | Hatch | incubation status + remaining time, or "no active incubation" |
  | 15 | Active slots | current/max entitlement, next unlock availability |
  | 22 | Help | opens `/pet help` |
  | 26 | Studio | shown only with `omnipet.admin.managepet` |
- `PlayerHubController` — one read per open via `taskQueue.submitLatest(playerId, "hub:view", ...)`. Coalescing removes only pending tasks whose key matches (`task/PerPlayerTaskQueue.java:68`), and the key must be namespaced (`:158-166`), so `hub:view` cannot swallow a pending `vault:view` or `slot:view` read. Reuses `PlayerRequestTracker` + the `expectedTop` guard pattern from `PlayerPetController.openVault` (`:81-101`) so a late async result cannot replace a newer view.

Hub summary data comes from **one** `PlayerState` read. `RepositoryHatchService.snapshot(UUID)` (`core/incubation/RepositoryHatchService.java:41`) returns the full `PlayerState`, which already carries `pets`, `desiredActivePetIds`, `slotEntitlements`, and `incubation` (`core/domain/PlayerState.java:11-21`). Derive the vault/slot tiles with `PetStorageSnapshot.from(state, limits)` — the same pure factory `RepositoryPetStorageService` uses — and read the hatch tile straight off `state.incubation()`. No second repository call, no second file read.

Tile clicks delegate to existing controllers (`playerPets.openVault`, `hatchController.open`, `slotPurchases.open`) rather than reimplementing anything. Studio tile calls `studio.openBrowse` after the same permission check the command performs.

Back navigation: each player GUI gains one `Back to hub` control in a free slot — vault slot 48 (45/49/50/53 are taken per `PlayerPetMenuRenderer.java:50-72`), hatch slot 18 (11/13/15/22 taken per `HatchMenuRenderer.java:45-75`), management slot 27 (10-16/22/31/35 taken per `PetManagementMenuRenderer.java:40-79`). Slot purchase already has "Back to vault" at 22 and keeps it — its flow is a vault sub-flow, not a hub peer.

Command routing: `AdminPetCommandParser` gains `OpenHub` and a `vault` literal.

- no args → `OpenHub`
- `<page>` → `PlayerPage` (unchanged)
- `vault` → `PlayerPage(1)`; `vault <page>` → `PlayerPage(page)`
- `admin browse` → unchanged

`AdminPetCommandParserTest.preservesOneBasedPlayerPageArguments` currently asserts no-args yields `PlayerPage(1)` (`:47,:50`) and must be updated in the same commit, plus new cases for `OpenHub` and `vault`.

`OmniPetCommandTree` (Phase 3) gains `vault [page]` so it appears in help and suggestions.

## Related Code Files

- Create: `gui/hub/HubInventoryHolder.java`, `gui/hub/HubMenuRenderer.java`, `gui/hub/HubMenuListener.java`, `player/PlayerHubController.java`
- Create: `gui/hub/HubMenuRendererTest.java`, `player/PlayerHubControllerTest.java`
- Modify: `command/AdminPetCommandParser.java` — `OpenHub` + `vault` literal
- Modify: `command/AdminPetCommandParserTest.java` — update no-args expectation, add hub/vault cases
- Modify: `command/OmniPetCommand.java` — dispatch `OpenHub`
- Modify: `command/OmniPetCommandTree.java` — add `vault [page]`
- Modify: `OmniPetPlugin.java` — construct the hub controller, register `HubMenuListener`, include it in the shutdown close/drain sequence next to the other controllers (`OmniPetPlugin.java:227-242`)
- Modify: `PlayerPetMenuRenderer`, `HatchMenuRenderer`, `PetManagementMenuRenderer` — back-to-hub control
- Modify: `text/MessageKey.java` — `hub.*`

## Implementation Steps

1. Add `OpenHub` to the parser sealed interface and the `vault` literal; update the parser test.
2. Build `HubInventoryHolder` mirroring the vault holder's immutability and revision binding.
3. Build `HubMenuRenderer` from `GuiItems` + catalog keys, with the Studio tile gated on permission at render time (not just click time, so it is not visible-but-dead).
4. Build `PlayerHubController` with `hub:view` coalescing, `PlayerRequestTracker`, and the `expectedTop` guard; add `release(UUID)` and `close()` matching the sibling controllers' lifecycle contract.
5. Add `HubMenuListener` following `PlayerPetMenuListener`: cancel all clicks, verify viewer, bound-check raw slot against top size, accept only LEFT/RIGHT, cancel drags touching the top inventory.
6. Wire construction, listener registration, and shutdown draining in `OmniPetPlugin`.
7. Add back-to-hub controls in the three renderers at the free slots identified above.
8. Add `vault [page]` to the command tree so help and tab-complete list it.
9. Tests: hub tile summaries reflect a given snapshot; Studio tile absent without permission; stale-revision click does not act; `hub:view` does not coalesce with `vault:view`; parser routes no-args → hub, `vault` → page 1, `vault 3` → page 3, `3` → page 3.
10. Update `CHANGELOG.md` with the `/pet` behavior change and the `/pet vault` addition.

## Success Criteria

- [x] `/pet` opens the hub; `/pet 2` and `/pet vault 2` open vault page 2.
- [x] Hub tiles show live vault/hatch/slot summaries.
- [x] Studio tile appears only with `omnipet.admin.managepet`.
- [x] Vault, hatch, and management expose back-to-hub without displacing an existing control.
- [x] Hub performs exactly one coalesced read per open and does not interfere with vault reads.
- [x] Hub joins the shutdown close/drain sequence.
- [x] Updated parser test passes; all other command tests pass unmodified.

Report: [`plans/reports/phase-06-player-hub-report.md`](../reports/phase-06-player-hub-report.md).
147 suites / 605 tests (+19). Hub is fully wired into the plugin lifecycle: construction, listener
registration, reload, and shutdown drain.

Deviations from the plan, both deliberate:

1. `OmniPetCommand` takes a `bindHub(HubTarget)` setter instead of a 14th constructor parameter, so
   the ten existing overloads stay byte-identical. An unbound hub falls back to the previous
   vault-first behavior, which is what the narrower overloads used by tests rely on.
2. The management screen's existing `BACK` action now returns to the vault page the player came from
   (`/pet vault`) rather than the hub. Back-to-hub is a new `HUB` action at slot 27. Without this,
   the two controls would have been indistinguishable in behavior.

Also recorded: `GUI_TITLE_HUB` was declared after the `manage.*` keys, producing a duplicate `gui:`
mapping in the generated `messages.yml` that the round-trip test caught. The generator now sorts by
path so an out-of-order enum constant can never corrupt the file again.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| `/pet` change breaks muscle memory | `/pet <page>` still works; `/pet vault` added; hub tile 1 is Vault; CHANGELOG entry. |
| New GUI reintroduces an unguarded click path | Listener copied from `PlayerPetMenuListener` guard-for-guard; stale-click test required. |
| Hub read competes with vault read | Distinct `hub:view` key; coalescing is per-key by design; asserted by test. |
| Hub missed in shutdown drain | Added beside the other controllers in `onDisable`; reviewed as part of Phase 7. |
| Back-to-hub overwrites a live control | Free slots verified against each renderer's current slot map before implementation. |
