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

Record the baseline, then land the two small fixes that need no new infrastructure: the hub slot-tile return bug and the hub's missing in-flight guard. Both are debt from the previous plan's Phase 6 and should not wait behind a config surface.

## Requirements

- Functional: cancelling a slot purchase opened from the hub returns to the hub; the hub refuses a second concurrent open.
- Non-functional: build result matches the recorded baseline apart from the two fixes and their tests.

## Architecture

### The slot-tile return bug

`PlayerHubController.click` calls `slotPurchases.open(player, 1)`. The `returnPage` argument is what the purchase flow later replays via `player.performCommand("pet " + holder.returnPage())` on cancel and on success (`PlayerSlotPurchaseController` cancel path and `completeMutation`). Passing `1` therefore means "return to vault page 1" — a player who opened the slot menu from the hub is silently relocated to the vault.

`returnPage` is an `int` threaded through `SlotPurchaseInventoryHolder`, so the fix must not simply overload it with a sentinel. Two options:

- **Chosen:** add a nullable/flagged origin to the holder — `SlotPurchaseOrigin { VAULT(page), HUB }` — and have the cancel/success path dispatch on origin. Explicit, testable, no magic number.
- Rejected: pass `returnPage = 0` as a hub sentinel. `SlotPurchaseInventoryHolder` would have to relax its positive-page invariant, weakening a real guard for a display concern.

### The hub in-flight guard

Every sibling controller holds a `Set<UUID>` guard: `PlayerPetController.mutationsInFlight` (`:36`), `PlayerHatchController.mutations`, `PlayerSlotPurchaseController.mutations` (`:43`), `PaperActiveSkillController.inFlight` (`:43`). `PlayerHubController` has none, so a player spamming a tile queues repeated `hub:view` reads. `submitLatest` coalescing limits the damage — pending duplicates collapse — so this is consistency and politeness, not a correctness bug.

Use `ConcurrentHashMap.newKeySet()`, matching the siblings. **Do not use `PlayerRequestTracker`** — verified at `task/PlayerRequestTracker.java`, its `begin()` unconditionally overwrites and returns a new token, and it exposes only `isCurrent`, `invalidate`, and `clear`. It is a stale-completion *filter*, not a mutex; it has no rejection path and cannot implement "drop a second concurrent open." The hub already holds one for its intended purpose — keep that, and add the set separately.

### The pinned contract string

`PlayerHubControllerContractTest.java:60` asserts `source.contains("slotPurchases.open(player, 1)")` — the exact call this phase deletes. That test **must** be updated to assert the origin-aware call instead. It is not a pre-existing test that survives untouched, and the implementer must not preserve a dead `open(player, 1)` to keep it green.

Deliberately **not** in this phase: any new config key, sound, or renderer change. Baseline first.

## Related Code Files

- Modify: `paper/player/PlayerHubController.java` — origin-aware slot open, `Set<UUID>` in-flight guard
- Modify: `paper/gui/player/SlotPurchaseInventoryHolder.java` — origin field
- Modify: `paper/gui/player/SlotPurchaseMenuRenderer.java` — thread origin through both screens
- Modify: `paper/player/PlayerSlotPurchaseController.java` — dispatch on origin at **all three** return points: cancel (`:153`), success (`:257`), and `STALE_QUOTE` reopen (`:270`)
- Modify: `paper/player/PlayerHubControllerContractTest.java` — line 60 pins the string this phase deletes
- Create: `paper/player/SlotPurchaseOriginTest.java`
- Read only (baseline): the five renderers, `config.yml`

## Implementation Steps

1. Record the build baseline: `gradlew.bat clean build --no-daemon --console=plain`. Note suite/test counts, JAR size, and SHA-256. This is the comparison point for every later phase.
2. Grep and record the current feedback surface: `playSound`, `showTitle`, `sendActionBar`, `BossBar` counts (expected 0), and every hardcoded value with its exact line, into `plans/reports/`. This is the Phase 2 worklist.
3. Add `SlotPurchaseOrigin` in `gui/player/`: `HUB`, or `VAULT` carrying the one-based page.
4. Thread origin through `SlotPurchaseInventoryHolder` and both renderer screens. `SlotPurchaseInventoryHolder:34` enforces `returnPage >= 1`; a `HUB` holder must therefore still carry a placeholder page. **State explicitly in code that the field is ignored for `HUB`**, so the placeholder does not read as meaningful — or extract navigation out of the holder entirely if that proves cleaner during implementation. Do not silently relax the invariant.
5. Dispatch on origin at all three return points (`:153`, `:257`, `:270`): `HUB` runs `pet`, `VAULT` runs `pet vault <page>`.
6. Add the hub in-flight guard as `ConcurrentHashMap.newKeySet()`; a second open while one is pending is dropped. Remove the entry on completion **and** on failure, or a failed open locks the hub permanently.
7. Update `PlayerHubControllerContractTest:60` to assert the origin-aware call.
8. Tests: cancelling from hub origin routes to the hub; cancelling from vault origin routes to that exact page; `STALE_QUOTE` reopen preserves origin; a second concurrent hub open is dropped; a *failed* open releases the guard.

## Success Criteria

- [ ] Baseline suite/test counts, JAR size, and SHA-256 recorded in `plans/reports/`.
- [ ] Feedback-surface and hardcoded-value worklist written to `plans/reports/`, each with an exact line.
- [ ] Cancelling a hub-opened slot purchase returns to the hub (asserted).
- [ ] Cancelling a vault-opened slot purchase returns to that page (asserted, unchanged behavior).
- [ ] `STALE_QUOTE` reopen preserves origin (asserted).
- [ ] A second concurrent hub open is dropped (asserted).
- [ ] A **failed** hub open releases the guard rather than locking the hub (asserted).
- [ ] `SlotPurchaseInventoryHolder` keeps its `returnPage >= 1` invariant; the placeholder page for `HUB` is documented in code as ignored.
- [ ] `PlayerHubControllerContractTest:60` updated to assert the origin-aware call; no dead `open(player, 1)` is left behind to satisfy it.
- [ ] Every other pre-existing test passes unmodified.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| Origin threading touches the purchase flow, which handles money | Only the *return navigation* changes. Quote, confirm, transaction ID, and reconciliation are untouched; a test asserts the transaction ID survives the confirm screen. |
| Missing the third return point | All three are enumerated with line numbers (`:153`, `:257`, `:270`) and each has an assertion. |
| A guard leak locks the hub | The guard must be released on failure as well as success; asserted by a failure-path test. |
| Relaxing the page invariant to carry a sentinel | The invariant stays. The `HUB` placeholder is documented as ignored rather than made meaningful. |
| Preserving a dead call to keep a pinned test green | The test is explicitly in the modify list with the reason stated. |
