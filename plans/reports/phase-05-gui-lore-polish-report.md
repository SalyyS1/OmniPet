# Phase 5 GUI lore polish

Plan: `plans/260803-1146-omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub/`
Recorded: 2026-08-03

## The italic fix is now visible

Phase 1 added `GuiItems` but migrated no renderer, so nothing changed in-game. This phase moved all
five renderers onto it, which is where the "sơ sài" impression actually goes away: every display name
and lore line now resolves `ITALIC` to `FALSE` instead of rendering vanilla italic.

Verified gone: `grep "private static ItemStack item("` over `omnipet-paper/src/main` returns nothing.

## Shipped

| File | Role |
| --- | --- |
| `gui/GuiColors.java` | The whole colour vocabulary. Renderers pick a role, not a colour. |
| `gui/player/VaultPetSummary.java` | Null-safe level/rarity read. Type-tests instead of casting. |
| `gui/player/SlotBalanceDisplay.java` | Typed balance so the renderer owns the wording, not the controller. |
| `text/Displays.java` | Enum→text in one place. |

Migrated: `PlayerPetMenuRenderer`, `HatchMenuRenderer`, `SlotPurchaseMenuRenderer`,
`PetManagementMenuRenderer`, `StudioInventoryRenderer` (+ `StudioStatScreens` via the shared helper).
`PetManagementMenuSupport.message` also migrated — the chat half deferred from Phase 2.

51 `gui.*` keys plus 3 `manage.*` keys added.

## Why not the projection

`PetProgressionProjection.read` requires `initialStamina` and `nowEpochMillis` and throws on a
malformed component — confirmed at `PetProgressionProjection:19,48,58`. A renderer that throws blanks
the whole vault page for one bad legacy pet. `VaultPetSummary` reads
`rawComponents.progression.level` and `rawComponents.hatching.rarityId` defensively and omits what it
cannot read. Nine tests cover: full pet, no components, non-numeric level, fractional level,
integer-valued double, out-of-range, non-map component, blank rarity, partial component.

This distinction surfaced while writing the contract test: a *partial* progression component makes
`PetManagementViewModel` throw, because management legitimately uses the projection. Absent-entirely
is fine; half-populated is not. The management fixture is fully populated and the comment says why —
the vault's tolerance is `VaultPetSummary`'s job, tested separately.

## Lore shape and colour

Value lines first, blank spacer, action hints last, so players read actions in a fixed position across
every menu. Static colours live in the message defaults where operators can edit them; only genuinely
state-dependent colours (vault overflow, provider availability, lock state, hatch readiness) are
applied in code.

Titles gained location context: `OmniPet ▸ Vault (1/3)`, `OmniPet ▸ Hatch`, `OmniPet ▸ Manage`.

## Player vs admin identity

Vault rows dropped the truncated instance UUID — noise for players — and show name, level, and rarity
instead. Full UUIDs stay in the management screen and Studio, where staff need them; a test asserts
the management row contains the complete UUID and no `...` fragment.

The two surviving `abbreviate` calls are Studio-only and unrelated to identity: one shortens a
300-character base64 blob for the editor slot, the other caps an inventory title length.

## Contract changes

`PetManagementMenuRenderer.Layout.title` and `Entry.name`/`Entry.lore` moved from `String` to
`Component`, which is required for catalog ownership. One assertion in
`PetManagementMenuContractTest` was updated to compare rendered plain text; the other three pass
unmodified, and the holder/action maps are untouched.

`SlotPurchaseMenuRenderer.selection` now takes `Function<EconomyProvider, SlotBalanceDisplay>` rather
than a pre-formatted `String`, moving that GUI text into the catalog.

## Verification

`gradlew.bat clean build --no-daemon --console=plain` — **BUILD SUCCESSFUL**, all guard tasks green.

| Module | Suites | Tests | Δ vs Phase 4 |
| --- | ---: | ---: | --- |
| `omnipet-core` | 63 | 256 | — |
| `omnipet-paper` | 82 | 330 | +3 suites, +22 tests |
| **Total** | **145** | **586** | +22 |

New suites: `VaultPetSummaryTest` (9), `DisplaysTest` (8), `RendererTextContractTest` (5). Existing
GUI and click tests pass with one updated assertion.

## Unresolved questions

None.
