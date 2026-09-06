---
phase: 5
title: "Command, GUI, config, i18n fixes"
status: pending
priority: P2
effort: "2.5d"
dependencies: [1]
---

# Phase 5: Command, GUI, config, i18n fixes

## Context Links

- Findings source: `plans/reports/from-scout-ux-to-planner-command-gui-config-defect-review-report.md`

## Overview

Close the player/operator-facing defect set: dead GUI buttons from out-of-range slots, permission drift between the two command entry points, 83 hardcoded English strings bypassing the vi.yml catalog, MiniMessage injection through pet names, and silent config-load truncations.

## Key Insights

- `gui/MenuLayout.java` handles out-of-range slots inconsistently (verified 2026-09-04): the guard at `:137-139` warns and falls back to the default slot, the guard at `:141` silently `continue`s, and neither path checks a negative slot. The silent path and the negative case are the dead-button bug; the warning path is the model to unify on.
- `OmniPetCommand.canUse` (`:557-566`) and `OmniPetAdminCommand` (`:63-75`) hardcode separate permission lists that have already drifted from `OmniPetCommandTree` — the tree is the truth the contract test proves; both surfaces should derive from it.
- 83 `sendMessage("OmniPet: ...")` literals bypass `MessageKey`/`lang/vi.yml` (parity currently 308/308 for cataloged keys); VI players see mixed language.
- Player-controlled names flow into MiniMessage unescaped (`render/Nameplate.java:59,89`, `text/Messages.java:73-81`) — tag injection (e.g. `<rainbow>`, click events) in nameplates and messages.
- `paper-plugin.yml` is processed through Groovy `expand()` (`omnipet-paper/build.gradle.kts:44-48`), so removing `omnipet.admin.explore` must not introduce a literal `$` — it would break the build.

## Requirements

- Functional: all user-visible strings resolve through the catalog with vi parity; command help/suggestions/permissions derive from the tree; GUI config errors are loud at load/reload.
- Non-functional: no behavior change for correctly configured servers; catalog migration keeps existing keys stable.

## Architecture

- **Catalog migration:** each hardcoded literal becomes a `MessageKey` + `lang/vi.yml` entry; add a contract test that scans command/controller sources for `sendMessage(` with string literals (or a stronger lint) so the class of bug cannot return; extend the existing 308-key parity test.
- **Tree-derived permissions:** `canUse` on both entry points delegates to `OmniPetCommandTree` node permissions; suggestions (`CommandSuggestions.java:54-63`) and help complete from the same walk; add missing tree entries (`slot [page]`, `stats all`).
- **MiniMessage safety:** escape/strip tags from player-provided names at the render boundary (`Messages`/`Nameplate`), not at storage — stored names stay raw.
- **Config loudness:** `MenuLayout` rejects out-of-range and negative slots with a load warning naming menu+button on every path; `config/GuiConfigLoader.java:196,208` reports float truncation; reload warnings extend beyond `runtime` to `render`/`gui` sections; `OmniPetConfigLoader.encode` includes `render.nameplateStatus`.

## Related Code Files

- Modify: `omnipet-paper/.../gui/MenuLayout.java`, `config/GuiConfigLoader.java`, `gui/player/PetManagementMenuRenderer.java` + `gui/player/PlayerPetMenuRenderer.java` (fold both duplicate `MenuButtonStyle.slot`/`.material` resolutions into MenuLayout), `gui/hatch/HatchMenuListener.java` (ClickType filter)

<!-- Updated: Validation Session 2 - GuiConfigLoader and PetManagementMenuRenderer paths corrected; MenuLayout finding narrowed; permissions decision recorded -->
- **Decided (validation session 2, 2026-09-04): where an entry point's hardcoded permission list is looser than the tree, the tree wins.** Every node that becomes stricter is listed in this phase's report and reaches the 3.0.0 release notes (Phase 10 step 3) so an operator can re-grant before players notice.
- Modify: `omnipet-paper/.../command/OmniPetCommand.java` (canUse from tree, raw enum at `:390` rendered via catalog), `OmniPetAdminCommand.java`, `OmniPetCommandTree.java` (add `slot [page]`, `stats all`), `CommandSuggestions.java`
- Modify: `omnipet-paper/.../text/Messages.java`, `render/Nameplate.java` (escape player input)
- Modify: `omnipet-paper/.../config/OmniPetConfigLoader.java` (encode nameplateStatus, reload warnings for render/gui), `MenuStyle.java`
- Modify: `omnipet-paper/src/main/resources/lang/vi.yml` + message catalog enum (83 new keys), catalog parity test
- Delete: dead `FoundationCommand`, `COMMAND_FOUNDATION_READY`, `omnipet.admin.explore` references; legacy null-accepting constructor in `gui/player/PlayerPetMenuListener.java:21-25,58,77`
- Modify (P3 batch): `OmniPetConfigLoader.java:363-367` (`numberMap` hardcodes the `formulaSamples.` error prefix — pass the real prefix), `command/AdminPetCommandParser.java:55-60` (validate arguments before the permission check so a typo reports usage, not "no permission"), `command/PlayerCommandRouter.java:84-88,113` (narrow the over-wide `IllegalArgumentException` catch that reports every saga IAE as "bad UUID"; guard the `slotPurchases` dereference), `command/SlotTransactionAdminCommandParser.java:33-37` (require canonical UUID spelling so admin transactions key on the form the repositories wrote), `studio/bukkit/PetStudioController.java:503` (two statements on one line)
- Modify: `docs/commands-and-permissions.md` (drift at `:37-41` — final content lands in Phase 8; here only what the code change itself invalidates)

## Implementation Steps

1. MenuLayout slot validation + load warning + test (out-of-range slot → warning, button skipped loudly).
2. Fold the duplicate layout code in `gui/player/PetManagementMenuRenderer.java:205-217` **and** `gui/player/PlayerPetMenuRenderer.java` into `MenuLayout` — there are two copies, and porting only one leaves the second free to drift again. `MenuLayout` has 5 consumers today; all keep compiling.
3. canUse/suggestions/help derive from tree; drift test comparing entry points to tree nodes; add missing nodes (`slot [page]`, `stats all`) and honor `visible()` in the walk; drop the stale comment at `OmniPetCommandTree.java:97-98` left by an earlier drift fix.
4. Catalog migration of 83 literals (mechanical, commit in batches by package) + vi.yml keys + extended parity test + literal-scan contract test. Includes the raw enum name rendered at `OmniPetCommand.java:390` — it is not a `sendMessage` literal, so the mechanical sweep misses it: give it a catalog key with a human-readable label per value.
5. MiniMessage escaping at render boundary + injection test (`<red>x` renders literally).
6. Config loader: nameplateStatus encode, float truncation warning, render/gui reload warnings.
7. ClickType filter in HatchMenuListener; dead command surfaces + legacy null-accepting menu constructor removed.
8. P3 batch: config `numberMap` prefix, parser ordering (validate before permission), narrowed IAE catch + `slotPurchases` guard, canonical-UUID requirement, one-statement-per-line in the studio controller — each with a focused test where behavior changes.
9. Full build + parity test green.

## Todo

- [ ] MenuLayout validation + loud config errors
- [ ] Both duplicate layout engines folded into MenuLayout
- [ ] Tree-derived canUse/help/suggestions + missing nodes
- [ ] 83 literals → catalog + vi.yml + parity/scan tests
- [ ] MiniMessage escaping for player names
- [ ] Config encode/reload warning fixes
- [ ] ClickType filter; dead surfaces + legacy null ctor removed
- [ ] P3 batch: numberMap prefix, validate-before-permission, narrowed IAE catch, canonical UUID, style fix
- [ ] Green: full build

## Success Criteria

- [ ] Literal-scan test finds zero uncataloged user-facing strings in command/controller packages
- [ ] vi.yml parity test green with all new keys translated
- [ ] Permission drift test proves both entry points match the tree
- [ ] An out-of-range GUI slot produces a named load warning instead of a silently dead button
- [ ] A float where the config wants an int is reported, not silently truncated
- [ ] `<red>test</red>` as a pet name renders as literal text everywhere

## Risk Assessment

- 83-string migration is wide but mechanical; risk is silent meaning drift in VI translations. Mitigation: keep EN string as the catalog default, translate in one reviewed batch; parity test blocks missing keys.
- Deriving canUse from the tree will tighten any node the entry points were accidentally looser on — decided as a fix (tree wins), so each tightened node goes in the phase report and the release notes, never silently.

## Security

- MiniMessage escaping closes a player-input injection vector (chat-component clicks/hover from names). Permission derivation removes drift-created permission holes.
