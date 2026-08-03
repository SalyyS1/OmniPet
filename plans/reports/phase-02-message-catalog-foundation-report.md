# Phase 2 message catalog foundation

Plan: `plans/260803-1146-omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub/`
Recorded: 2026-08-03

## Shipped

| File | Role |
| --- | --- |
| `text/MessageKey.java` | 63 keys, each carrying its built-in MiniMessage default. Single source of truth. |
| `text/MessageCatalog.java` | Lenient YAML loader, `line`/`lore`, per-key cache for argument-free lookups. |
| `text/MessageCatalogFile.java` | Generates `messages.yml` from the enum; never overwrites an existing file. |
| `text/Messages.java` | Static accessor with `bind`/`unbind`/`catalog`, `of(...)` = `Placeholder.unparsed`. |

`messages.yml` is generated at runtime rather than shipped as a resource, so there is no second copy
of the text that could drift from `MessageKey`. A round-trip test writes the file, reloads it, and
asserts every key matches its default byte for byte.

## Migrated (player-facing chat only)

| Controller | Sites | Note |
| --- | ---: | --- |
| `player/PlayerPetController` | 5 | `words(...)` retained for the status placeholder. |
| `player/PlayerHatchController` | 19 (1 helper) | `message(Player, MessageKey)` replaced the color-carrying helper. |
| `player/PlayerSlotPurchaseController` | 14 (1 helper) | Same shape. |
| `skill/PaperActiveSkillController` | player half | `finish(UUID, Component)` now takes a rendered line. |
| `incubation/action/IncubationActionItemController` | redemption only | Delivery receipts untouched. |
| `command/FoundationCommand` | 1 | — |

Untouched by design: `ReleaseAdminController`, `SlotTransactionAdminController`,
`HatchAdminController`, `PaperCultivationAdminCommandTarget`, `PetCultivationItemController`, and the
`CommandSender` overload in `PaperActiveSkillController`. `MigratedControllerTextContractTest`
asserts none of them imports `Messages`.

Studio (15 sites) and the GUI renderers stay for Phases 4 and 5. A test asserts no `studio.*` key
exists yet, so the enum cannot grow ahead of its owner.

## Wiring

`onEnable` — writes `messages.yml` if absent (symlink-guarded, mirroring the `config.yml` guard),
loads it, binds the catalog. `reloadRuntime` — stages a new catalog **before** `studio.reload()`
can bail, then binds it only after every other staged value is applied; a failed reload leaves the
previous catalog live. `onDisable` — unbinds, so a disabled plugin cannot serve stale text.

## Text-identity check

Every default reproduces today's rendered string with only the MiniMessage colour tag added. Two
deliberate deviations:

1. `hatch.claim-failed` — when a throwable carried a null or blank message the old path rendered
   `Claim failed: null`. It now falls back to the exception's simple name, matching how every other
   failure path in the plugin reports a detail.
2. `hatch.claim-result` split into a green success key and a yellow rejected key. The old code chose
   the colour from `result.succeeded()`; a catalog value cannot branch, so the branch moved to the
   key. Both strings are unchanged.

## Verification

`gradlew.bat clean build --no-daemon --console=plain` — **BUILD SUCCESSFUL**, all guard tasks green.

| Module | Suites | Tests | Δ vs Phase 1 |
| --- | ---: | ---: | --- |
| `omnipet-core` | 62 | 238 | — |
| `omnipet-paper` | 74 | 255 | +4 suites, +32 tests |
| **Total** | **136** | **493** | +32 |

New suites: `GuiItemsTest` (6), `MessageCatalogTest` (14), `MessageKeyDefaultsTest` (6),
`MigratedControllerTextContractTest` (6). Zero failures, errors, or skips.

Covered: default-vs-file precedence; unknown key warns without throwing; a non-text value keeps the
default; a malformed tag degrades to literal text; `<red>evil</red><click:run_command:/op me>` in a
placeholder renders literally; argument-free lines are cached; list values become one line each;
an absent file yields defaults; a generated file round-trips every key; an operator edit is never
overwritten; an unbound accessor throws.

## Unresolved questions

None.
