---
phase: 3
title: "Command help and tab-complete"
status: complete
priority: P1
effort: "1d"
dependencies: [2]
---

# Phase 3: Command help and tab-complete

## Overview

Give `/pet` a permission-filtered `help` listing and Brigadier tab suggestions, both generated from one declarative command spec so they cannot drift from each other or from the dispatcher.

## Requirements

- Functional: `suggest(...)` returns branch names, subcommands, and static keywords filtered by the sender's permissions and by the token being typed; `/pet help [page]` prints the same tree, paged.
- Non-functional: pure data + pure functions, unit-testable without a running server; no disk or repository access from `suggest`.
- Scope decision (user): static keywords only. No UUID enumeration, no online-player names, no disk scans — `suggest` must never do I/O on the main thread.

## Architecture

`OmniPetCommand` implements `BasicCommand`, which already declares
`default Collection<String> suggest(CommandSourceStack, String[])` (verified in paper-api 1.21). It is simply never overridden, so Paper offers nothing. Registration goes through `Commands#register(String, Collection<String>, BasicCommand)` (`OmniPetPlugin.java:268`), which wires suggestions automatically once the method exists.

`OmniPetCommand` is already 556 lines with 10 constructor overloads. Do **not** grow it. New siblings in `paper/command/`:

- `CommandSpec` — immutable tree node: literal, permission, argument placeholders, one-line description, console-allowed flag. Placeholders are display-only hints (`<player-uuid>`), never suggestion values.
- `OmniPetCommandTree` — the single spec instance describing the real dispatcher, transcribed from the existing branches:
  - player: `(no args)`, `<page>`, `hatch [main|off|claim|refresh|use-main|use-off]`, `slot`, `skill <pet-uuid> <binding-id>`, `help [page]`

  The tree describes only what the dispatcher accepts *today*. `vault [page]` is deliberately absent here and is added by Phase 6 together with its dispatcher branch, so help and suggestions never advertise a command that does not yet work.
  - `admin browse` — `omnipet.admin.managepet`
  - `admin reload` — `omnipet.admin.reload`
  - `admin item reducer|instant|candy|breakthrough ...` — `omnipet.admin.item` (+ `omnipet.admin.cultivation` for candy/breakthrough, matching `OmniPetCommand.java:353-356`)
  - `admin hatch inspect|reduce|set|complete|cancel ...` — `omnipet.admin.inspect` for `inspect`, `omnipet.admin.manageegg` for the rest (`HatchAdminCommandParser:9-10`)
  - `admin skill pending|rollback ...` — `omnipet.admin.skill` (`PaperActiveSkillController:83,88`)
  - `admin cultivation pending|review|recover ...` — `omnipet.admin.cultivation` (`PaperCultivationAdminCommandTarget:35-36`)
  - `admin release list|recover|reconcile ...` — `omnipet.admin.release` (`ReleaseAdminCommandParser:12-14`)
  - `admin transactions [limit] [cursor]` and `admin reconcile <uuid> charge|no-charge|refund|sync` — `omnipet.admin.reconcile`
- `CommandSuggestions` — pure `suggest(CommandSpec root, Predicate<String> hasPermission, boolean isPlayer, String[] args)`. Walks matched literals, returns children whose permission passes and whose name starts with the partial token (case-insensitive). At an argument position it returns empty rather than guessing.
- `CommandHelp` — pure `List<HelpLine> lines(CommandSpec, Predicate<String>, boolean isPlayer)` plus paging math. Rendering uses `MessageKey.HELP_*` from Phase 2.

`OmniPetCommand` gains exactly two additions: a `help` branch in `execute` (placed before the `AdminPetCommandParser` fallthrough) and an overridden `suggest` delegating to `CommandSuggestions`. `permission()` keeps returning `null` so per-branch checks stay authoritative; `canUse` is unchanged.

Console handling: spec nodes carry `playerOnly`. Console sees only `admin *` branches, matching the existing "a player is required" guards (`OmniPetCommand.java:313`, `:330`, `:400`).

## Related Code Files

- Create: `command/CommandSpec.java`, `command/OmniPetCommandTree.java`, `command/CommandSuggestions.java`, `command/CommandHelp.java`
- Create: `command/CommandSuggestionsTest.java`, `command/CommandHelpTest.java`, `command/OmniPetCommandTreeContractTest.java`
- Modify: `command/OmniPetCommand.java` — add `help` dispatch + `suggest` override only
- Modify: `text/MessageKey.java` — `help.header`, `help.line`, `help.footer`, `help.empty`, `help.page-invalid`
- Modify: `command/FoundationCommandContract.java` — expose the spec root so the plugin and tests share one instance

## Implementation Steps

1. Define `CommandSpec` as a record with a `List<CommandSpec>` children list and a builder for readable nesting.
2. Transcribe `OmniPetCommandTree` from the dispatcher branches listed above. Every literal and permission is copied from real code, not invented.
3. Implement `CommandSuggestions`: empty `args` or a trailing empty token → all permitted root children; partial token → case-insensitive prefix filter; fully matched literal with more input → recurse; argument position → empty collection.
4. Implement `CommandHelp`: flatten the permitted tree depth-first into `usage + description` lines, 8 lines per page, clamp the requested page, report total pages.
5. Add `help` to `execute` before the parser fallthrough, so `/pet help` cannot be mistaken for a page number. Reject non-numeric page arguments with `help.page-invalid`.
6. Override `suggest`, delegating with `sender::hasPermission` and `sender instanceof Player`.
7. `OmniPetCommandTreeContractTest` asserts every permission string in the tree is declared in `paper-plugin.yml` — a typo'd node would otherwise silently hide a branch from everyone.
8. Tests: unprivileged player sees no `admin` node; `omnipet.admin.release` alone exposes `admin` → `release` and nothing else; `/pet ad` suggests `admin`; `/pet admin hatch ` suggests all five verbs, `inspect` gated separately; console sees no `hatch`/`slot`/`skill`; argument positions return empty; help paging clamps.

## Success Criteria

- [x] Tab-complete works for player and admin branches, filtered by permission and by prefix.
- [x] Suggestions perform no I/O and no UUID enumeration.
- [x] `/pet help` lists only permitted commands with descriptions, paged.
- [x] Every tree permission exists in `paper-plugin.yml` (asserted).
- [x] `OmniPetCommand` grew by only the two additions; no constructor overload changed.
- [x] Existing command tests still pass unmodified.

Report: [`plans/reports/phase-03-command-help-and-tab-complete-report.md`](../reports/phase-03-command-help-and-tab-complete-report.md).
139 suites / 522 tests (+29). `OmniPetCommand` 556 → 569 lines; rendering lives in a new
`CommandHelpRenderer` sibling so the class does not absorb it.

Beyond the plan: grouping literals (`admin`, `admin item`, …) carry no permission and derive
visibility from their children, and the contract test also pins every admin *verb* to the parser
that matches it — not just the branch literals.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Spec drifts from dispatcher | Contract test ties permissions to the descriptor; help and suggest share one tree, so a stale branch is visible in both. |
| Suggesting a branch the dispatcher rejects | Transcribe from code, not docs; add a test per admin branch keyword set. |
| `help` shadowing a valid page argument | `help` is matched before numeric parsing; pages remain digits-only. |
