# Phase 3 command help and tab-complete

Plan: `plans/260803-1146-omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub/`
Recorded: 2026-08-03

## Root cause confirmed

`OmniPetCommand` implements `BasicCommand` but never overrode
`default Collection<String> suggest(CommandSourceStack, String[])`, so Paper had nothing to offer.
`Commands#register(String, Collection, BasicCommand)` (`OmniPetPlugin:268`) wires suggestions
automatically once the method exists — no registration change was needed.

## Shipped

| File | Role |
| --- | --- |
| `command/CommandSpec.java` | Immutable tree node. `invocable` = can run it; `visible` = can run it **or** something under it, so a grouping literal hides when every child is denied. |
| `command/OmniPetCommandTree.java` | The one tree, transcribed from the live dispatcher and each parser's permission constant. |
| `command/CommandSuggestions.java` | Pure prefix + permission filter. No I/O, no UUID enumeration. |
| `command/CommandHelp.java` | Pure depth-first flatten + paging math, 8 lines per page. |
| `command/CommandHelpRenderer.java` | Catalog rendering, kept out of `OmniPetCommand`. |

`OmniPetCommand` grew by exactly 13 lines: a `help` branch placed before every numeric parse, and a
`suggest` override delegating to `CommandSuggestions`. 556 → 569 lines. No constructor overload
changed; `permission()` still returns `null` so per-branch checks stay authoritative.

Five new `help.*` keys were added to `MessageKey`.

## Design notes

**Grouping vs runnable.** `admin`, `admin item`, `admin hatch`, `admin skill`, `admin cultivation`,
and `admin release` are `group()` nodes: they carry no permission of their own and are never listed
as runnable commands. Their visibility is derived from their children, so a sender holding only
`omnipet.admin.release` sees `admin` → `release` and nothing else. A contract test asserts no
grouping literal carries a permission.

**Cultivation item types.** The dispatcher gates all four item types on `omnipet.admin.item`, then
additionally requires `omnipet.admin.cultivation` for `candy` and `breakthrough`
(`OmniPetCommand:352-356`). The tree encodes both permissions on those two nodes, so an
item-only admin is suggested `reducer` and `instant` only.

**Console.** Spec nodes carry `playerOnly`, matching the existing "a player is required" guards.
Console sees `help` and the `admin` subtree; `admin browse` is the single player-only admin node
(`OmniPetCommand:465`), and a test pins that list so a future branch cannot quietly join it.

**Argument positions return nothing.** `/pet skill ` suggests nothing rather than guessing a pet
UUID. An unmatched completed token stops the walk instead of restarting at the root, so `/pet 2 `
suggests nothing rather than re-offering `hatch`.

**`vault [page]` is deliberately absent.** The dispatcher has no such branch yet; Phase 6 adds the
literal and the branch together. Two tests assert its absence in both the tree and the dispatcher,
so the omission is intentional rather than forgotten.

## Contract tests

`OmniPetCommandTreeContractTest` ties the declaration back to the code it claims to describe:

- every permission in the tree is declared in `paper-plugin.yml`
- every top-level literal is matched by the dispatcher
- every admin branch literal is matched by the dispatcher or one of its parsers
- every admin verb (`inspect`/`reduce`/`set`/…, `list`/`recover`/`reconcile`, `pending`/`rollback`,
  `pending`/`review`/`recover`, `candy`/`breakthrough`) appears in the source that parses it
- no duplicate sibling literal
- `admin browse` is the only player-only admin node
- grouping literals carry no permission

## Verification

`gradlew.bat clean build --no-daemon --console=plain` — **BUILD SUCCESSFUL**, all guard tasks green.

| Module | Suites | Tests | Δ vs Phase 2 |
| --- | ---: | ---: | --- |
| `omnipet-core` | 62 | 238 | — |
| `omnipet-paper` | 77 | 284 | +3 suites, +29 tests |
| **Total** | **139** | **522** | +29 |

New suites: `CommandSuggestionsTest` (11), `CommandHelpTest` (10), `OmniPetCommandTreeContractTest`
(8). Every pre-existing command test passes unmodified.

## Unresolved questions

None.
