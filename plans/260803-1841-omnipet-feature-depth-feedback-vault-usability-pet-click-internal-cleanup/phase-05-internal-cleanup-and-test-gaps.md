---
phase: 5
title: "Internal cleanup and test gaps"
status: pending
priority: P2
effort: "1d"
dependencies: [2, 3]
---

# Phase 5: Internal cleanup and test gaps

## Overview

Pay down the debt the previous overhaul created and close the coverage blind spots. No user-visible change: every item here is either a duplicate collapsed to one implementation or a test for behavior that already ships.

Sequenced after Phases 2 and 3 so it can also absorb any duplication those phases introduce, rather than being invalidated by them.

## Requirements

- Functional: no behavior change. Rendered output, message text, and command routing stay byte-identical.
- Non-functional: exactly one countdown formatter repo-wide; no silent catch without a stated reason; named blind spots covered.

## Architecture

### The duplicated countdown formatter

`grep -rn 86_400` returns two copies of the same day/hour/minute/second formatter:

- `gui/hatch/HatchMenuRenderer.java:112`
- `gui/hub/HubMenuRenderer.java:122`

The second is self-inflicted — introduced by the previous plan's Phase 6 when the hub tile needed the same countdown. Collapse to one `paper/text/Durations.java` helper beside `Displays`. Both call sites must produce byte-identical output afterwards, asserted by a test that pins the existing format strings (`%dd %02dh %02dm %02ds` and `%02dh %02dm %02ds`).

Also collapse the decimal formatter, which **does** recur — verified at `PetManagementMenuRenderer.java:180` and `StudioStatScreens.java:163`, both `BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()`. It belongs next to `Durations` as a `decimal` helper.

### Silent failure paths

`MessageCatalog.render` (`:114`) catches `RuntimeException` and returns literal text with no log. That is deliberate for the *player* — a broken tag must never throw into a click handler — but it hides the cause from the *operator*, who has no way to learn which key is malformed.

Adding a warning is more invasive than it looks: `MessageCatalog` is immutable with only `values` + `cache` (`:34-40`), and the `Consumer<String> warnings` sink exists solely on the static `load`/`parse` entry points. A per-key warning requires threading a sink through the constructor **and** `defaults()` — which is called from `RendererTextContractTest.java:43` and `PetManagementMenuContractTest.java:36`, so both test call sites change.

Note also that argument-free lookups are already memoised at `:99`, so those keys already render once. Only resolver-carrying calls re-render, which narrows the benefit — warn once per key regardless, keyed so a repeatedly-drawn broken lore line does not spam the console per frame.

Audit every other `catch` and split into two groups: deliberate ignores, which get a comment stating why, and accidental swallows, which get a log. Do not add an unconditional log inside a render or tick path.

### Enum-to-text stragglers — named, not grepped for

Five player-facing copies survive, verified:

| Site | Line content |
| --- | --- |
| `player/PlayerPetController.java:315` | `value.name().toLowerCase(Locale.ROOT).replace('_', ' ')` |
| `player/PlayerSlotPurchaseController.java:321` | same |
| `player/PlayerHatchController.java:390` | same |
| `skill/PaperActiveSkillController.java:299` | same |
| `management/PetManagementMenuSupport.java:164` | `outcome.status().name().toLowerCase(...).replace('_', ' ')` |

Route each through `Displays.words`. Respect the operator-owned exclusions already asserted by `MigratedControllerTextContractTest.java:30-35` — audit strings deliberately keep their raw enum form.

### Test blind spots

Cover behavior that already ships and has real branching:

| Target | Why it matters |
| --- | --- |
| `PlayerHubController` routing | Every tile destination; the slot-origin fix from Phase 1 |
| `VaultPetFilter` / `VaultSortOrder` | Phase 3's comparators — total, stable, tie-broken |
| `FeedbackService` rate limiting | Phase 2's burst guard |
| `GuiConfigLoader` | Clamping, fallback, malformed input |
| `PetInteractListener` hand filtering | Phase 4's off-hand double-open guard — no new index to test, `InteractionIndex` already ships |
| `Durations` | Byte-identical output for both call sites |

Do **not** chase a coverage percentage. Cover branching logic; skip getters and records with no behavior.

## Related Code Files

- Create: `paper/text/Durations.java` + `DurationsTest.java`
- Modify: `paper/gui/hatch/HatchMenuRenderer.java:110-121` — delete local `format`, call `Durations`
- Modify: `paper/gui/hub/HubMenuRenderer.java:120-131` — delete local `format`, call `Durations`
- Modify: `paper/gui/player/PetManagementMenuRenderer.java:180` and `paper/studio/bukkit/StudioStatScreens.java:163` — use the shared `decimal` helper
- Modify: `paper/text/MessageCatalog.java` — per-key warning; **constructor and `defaults()` gain a sink**
- Modify: `paper/gui/RendererTextContractTest.java:43`, `paper/gui/player/PetManagementMenuContractTest.java:36` — `defaults()` call sites
- Modify: the five named straggler sites above
- Create: the test suites named below

## Implementation Steps

1. Add `Durations` with the two existing format strings, unchanged, plus the `decimal` helper.
2. Migrate both countdown call sites and both decimal call sites; delete all four private copies.
3. Add `DurationsTest` pinning current output for zero, sub-minute, sub-day, multi-day, and negative input (negative currently clamps to zero via `Math.max` — **preserve that**).
4. Thread a warning sink through `MessageCatalog`'s constructor and `defaults()`; update the two test call sites; add the per-key warning in `render`, keyed so a repeatedly-drawn broken line warns once.
5. Audit remaining `catch` blocks; comment the deliberate ones, log the accidental ones, no unconditional log in a render or tick path.
6. Migrate the five named stragglers to `Displays.words`; leave operator/audit strings alone per `MigratedControllerTextContractTest:30-35`.
7. Write the test suites in the table.
8. Full clean build; confirm counts only grow and no rendered text changed.

## Success Criteria

- [ ] `grep -rl 86_400 omnipet-paper/src/main` returns exactly **one** file. (`omnipet-core/.../IncubationDurationParser.java:61` contains `86_400_000L` and is deliberately out of scope — a repo-wide count would never reach 1.)
- [ ] Both hatch and hub countdowns produce byte-identical output to today (asserted).
- [ ] Both decimal call sites use the shared helper; no `stripTrailingZeros` copy remains in a renderer.
- [ ] A malformed message value warns **once per key**, not per render (asserted).
- [ ] Every remaining `catch` either logs or carries a comment explaining the deliberate ignore.
- [ ] All five named stragglers route through `Displays`; operator/audit strings unchanged and `MigratedControllerTextContractTest` still green.
- [ ] Each blind-spot target in the table has a direct test.
- [ ] Test counts grow; no existing test is weakened or deleted to pass.
- [ ] No rendered text changes anywhere (this is the phase's defining constraint).

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| "Cleanup" silently changes rendered output | `DurationsTest` pins the exact format strings before migration; the phase's criteria forbid text changes. |
| Threading a sink through `defaults()` breaks two test files | Both call sites are named in the modify list. |
| Warning-per-render spams the console | Keyed, fires once per key; repeat-render test. |
| Adding a log to a hot path costs performance | No unconditional log inside a render or tick. |
| Chasing coverage produces meaningless tests | The table names specific branching behavior; getters and dumb records are excluded. |
| Migrating an operator/audit string breaks the audit trail | Operator strings are out of scope; `MigratedControllerTextContractTest:30-35` guards it. |
