# Phase 6 player hub

Plan: `plans/260803-1146-omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub/`
Recorded: 2026-08-03

## What shipped

| File | Role |
| --- | --- |
| `gui/hub/HubInventoryHolder.java` | Mirrors `PlayerPetInventoryHolder`; snapshots its action map. |
| `gui/hub/HubView.java` | The one-read derivation. |
| `gui/hub/HubMenuRenderer.java` | 27-slot hub: Vault (11), Hatch (13), Active slots (15), Help (22), Studio (26). |
| `gui/hub/HubMenuListener.java` | Guard-for-guard copy of `PlayerPetMenuListener`. |
| `player/PlayerHubController.java` | One coalesced read under `hub:view`; tiles delegate to existing controllers. |

`AdminPetCommandParser` gained `OpenHub` and the `vault` literal; `OmniPetCommand` gained a `bindHub`
setter so its ten existing constructor overloads stay unchanged; the tree gained `vault [page]`.

## The `/pet` contract change

`/pet` (no args) now opens the hub. `/pet <page>` and the new `/pet vault [page]` keep direct vault
access. When no hub is bound — the narrower constructor overloads used by tests — `/pet` falls back
to the previous vault-first behavior, so the change is opt-in per wiring rather than a silent
behavioral break.

## One read per open

`RepositoryHatchService.snapshot` returns the full `PlayerState` carrying pets, desired-active IDs,
entitlements, and incubation. `HubView.from(state, limits)` derives the storage half with
`PetStorageSnapshot.from` — the same pure factory the vault uses — and reads the hatch tile straight
off `state.incubation()`. One repository call, no new API.

The read is coalesced under its own namespaced key (`hub:view`), so it cannot swallow a pending
`vault:view` or `slot:view` read. `PlayerRequestTracker` + the `expectedTop` guard keep a late async
result from replacing a newer view, matching the vault controller's discipline.

## Tile routing

Clicks delegate: `VAULT → playerPets.openVault`, `HATCH → hatchController.open`, `SLOTS →
slotPurchases.open`, `HELP → performCommand("pet help")`, `STUDIO → studio.openBrowse` — with the
permission re-checked at click time as well as render time, so a revoked permission cannot act.

## Back-to-hub controls

Vault slot 48 (45/49/50/53 taken), hatch slot 18 (11/13/15/22 taken), management slot 27
(10-16/22/31/35 taken). Slot purchase keeps its "Back to vault" — it is a vault sub-flow, not a hub
peer, exactly as the plan specified.

Back-to-hub in the management screen uses a new `HUB` action type; its existing `BACK` action now
returns to the vault page the player came from (`/pet vault`) instead of the hub, so muscle memory
survives.

## The one surprise

`GUI_TITLE_HUB` was declared after the `manage.*` keys, so the generated `messages.yml` emitted a
second `gui:` mapping — SnakeYAML rejects duplicate keys and the round-trip test caught it. Fixed
twice: the key moved into the gui section, and `MessageCatalogFile.defaultDocument()` now sorts keys
by path, so an out-of-order enum constant can never produce a duplicate-key document again.

## Verification

`gradlew.bat clean build --no-daemon --console=plain` — **BUILD SUCCESSFUL**, all guard tasks green.

| Module | Suites | Tests | Δ vs Phase 5 |
| --- | ---: | ---: | --- |
| `omnipet-core` | 63 | 256 | — |
| `omnipet-paper` | 84 | 349 | +2 suites, +19 tests |
| **Total** | **147** | **605** | +19 |

New suites: `HubViewTest` (9), `PlayerHubControllerContractTest` (5). `AdminPetCommandParserTest`
updated to the accepted contract change (no-args → hub, `vault` literal cases) plus three new tests;
`CommandHelpTest`/`CommandSuggestionsTest`/`OmniPetCommandTreeContractTest` updated from their Phase 3
"vault absent" form to "vault present". All other command tests pass unmodified.

## Unresolved questions

None.
