---
phase: 4
title: "Pet click interaction"
status: pending
priority: P2
effort: "0.5d"
dependencies: [2]
---

# Phase 4: Pet click interaction

## Overview

Right-clicking your own rendered pet opens its management screen.

**This phase is much smaller than it first appeared.** The reverse lookup, the ownership guard, and the concurrency guard all already exist and are already wired. What is missing is one exposure method, one listener, and one hand filter. An earlier draft of this phase proposed building a parallel index; that was wrong and is recorded below so the mistake is not repeated.

## Requirements

- Functional: right-clicking your own pet opens that pet's management screen, identical to right-clicking its vault row.
- Non-functional: no new index, no new record, no duplicated state. Reuse `InteractionIndex`.
- Compatibility: clicking a non-OmniPet entity must not cancel the event.

## Architecture

### What already exists — verified

| Component | Location | Status |
| --- | --- | --- |
| Entity → identity lookup | `omnipet-core/.../runtime/InteractionIndex.java` — `register` `:13`, `resolve` `:27`, `unregister` `:31`, `removeOwner` `:38`, all `synchronized` | Exists, thread-safe |
| Identity payload | `InteractionIdentity` — carries `ownerId`, `petInstanceId`, **and `rendererGeneration`** | Exists |
| Population | `PetActivationService.java:107` on spawn | Wired |
| Purge | `PetActivationService.java:129,141` on remove | Wired |
| Construction | `PaperRuntimeBootstrap.java:30` | Constructed, **reference discarded** |
| Ownership enforcement | `PetManagementMenuController.java:61` passes `(playerId, playerId, petId)` — viewer==owner is hardcoded | Already structurally impossible to open another player's pet |
| Concurrency guard | `PetManagementMenuController.java:54-59` `lifecycle.beginOpen` | Already rejects concurrent opens |

**Rejected approach:** a new `PetEntityRef` record plus a coordinator-owned `Map<UUID, PetEntityRef>`. It would duplicate a shipped index, allow the two to disagree, and — because `PetEntityRef` omitted `rendererGeneration` — would have *reintroduced* the stale-generation hole it claimed to close.

### The actual change

1. `PaperRuntimeBootstrap` retains the `InteractionIndex` it already builds instead of discarding it.
2. `PaperPetRuntimeCoordinator` exposes `Optional<InteractionIdentity> petFor(UUID entityId)` delegating to `InteractionIndex.resolve`. Delegation only — no new state, so no new locking question. `resolve` is already `synchronized`; do **not** additionally mark `petFor` synchronized.
3. `PetInteractListener` handles `PlayerInteractEntityEvent`.

### Listener guards, in order

1. **`event.getHand() != EquipmentSlot.HAND` → return.** This is the highest-value guard in the phase, not an afterthought. `PlayerInteractEntityEvent` fires **once per hand**; without it, one right-click opens the screen twice and the second open trips `lifecycle.beginOpen`, so the player sees "Another management request is already processing." on every legitimate click. A burst test does not catch this — it must be tested per-hand.
2. Look up the entity via `petFor`. Empty → `return` **without** cancelling, so other plugins see an untouched event.
3. Ours → cancel the event to suppress vanilla interaction.
4. Validate `rendererGeneration` against the current generation. A stale generation means the pet was re-rendered; treat as not-ours and return.
5. Open via `PetManagementMenuController.open(player, petId)`.

Guards 4 and 5 in the earlier draft — an ownership check and a new in-flight guard — are **defense in depth only**, because `PetManagementMenuController` already enforces both. Keep the ownership assertion as a test, not as new listener code.

`PlayerInteractAtEntityEvent` is deliberately not registered. It has its own `HandlerList`, so a `PlayerInteractEntityEvent` handler never receives it — the earlier claim that it "fires twice for one click" was wrong about the mechanism, though the conclusion stands.

### The pre-existing message conflict

An earlier draft required "no message" on a rejected click, to avoid confirming whose pet it is. But `lifecycle.beginOpen` already sends "Another management request is already processing." on a concurrent open. These conflict.

**Resolution:** keep the existing message. It fires on *concurrency*, not on *ownership*, so it leaks nothing about other players' pets — and suppressing it would mean special-casing a shipped, tested path for no security gain. The no-message rule applies only to ownership rejection, which cannot occur anyway.

### Out of scope

No riding, no `RideService`, no passive triggers, no left-click, no shift-click. `capabilities()` keeps `riding=false`.

## Related Code Files

- Create: `paper/gui/pet/PetInteractListener.java` + `PetInteractListenerTest.java`
- Modify: `paper/runtime/PaperRuntimeBootstrap.java` — retain the index reference
- Modify: `paper/runtime/PaperPetRuntimeCoordinator.java` — add `petFor` delegation
- Modify: `OmniPetPlugin.java` — register the listener
- **Not created:** `PetEntityRef`, `PetEntityIndexTest` — the index exists

## Implementation Steps

1. Retain the `InteractionIndex` reference in `PaperRuntimeBootstrap`; thread it to the coordinator.
2. Add `petFor(UUID)` delegating to `resolve`. No new state, no new lock.
3. Add `PetInteractListener` with the guards above, hand filter first.
4. Register in `onEnable` beside the other GUI listeners.
5. Add `FeedbackCategory.PROGRESS` on a successful open (Phase 2 dependency).
6. Decide the pre-existing index leak: `PetActivationService.remove()` (`:137-147`) returns `false` on exception leaving the entry, and `InteractionIndex.removeOwner` (`:38`) is never called from anywhere. Either wire `removeOwner` into owner-quit, or record it as accepted because entity UUIDs are never reused. **State the decision in the phase report.**
7. Tests: **one open per right-click across both hands** (the D9 regression); own pet opens management; unknown entity ignored without cancelling; stale `rendererGeneration` resolves as not-ours; left-click does nothing; ownership is enforced (asserting existing behavior).

## Success Criteria

- [ ] One right-click opens exactly **one** management screen; the off-hand event is filtered (asserted per-hand).
- [ ] Right-clicking your own pet opens its management screen.
- [ ] Clicking a non-OmniPet entity does not cancel the event (asserted).
- [ ] A stale `rendererGeneration` is treated as not-ours (asserted).
- [ ] `petFor` delegates to `InteractionIndex.resolve` and adds no new state (asserted by review).
- [ ] No `PetEntityRef` or second index exists.
- [ ] Left-click unchanged; `riding=false` unchanged.
- [ ] Only `PlayerInteractEntityEvent` registered (asserted by grep).
- [ ] The index-leak decision is recorded with its reasoning.

## Risk Assessment

| Risk | Mitigation |
| --- | --- |
| **Double-open from the off-hand event** | `getHand() != HAND` is guard #1, tested per-hand. This is the most likely failure and the earlier draft missed it entirely. |
| Duplicating the existing index | Explicitly rejected in Architecture with the reason recorded. |
| Dropping `rendererGeneration` | `InteractionIdentity` already carries it; guard #4 validates it. |
| Interfering with other plugins | Unknown entities return before `setCancelled`; asserted. |
| **`Interaction` entity may not deliver this event** | See Unresolved. The Phase 6 smoke item for this is **blocking**, not optional. |
| Pre-existing index leak becomes a correctness issue | Step 6 forces an explicit decision rather than silence. |

## Unresolved

Does a Paper `Interaction` entity deliver `PlayerInteractEntityEvent` — not only `PlayerInteractAtEntityEvent` — when it is a passenger of an invisible marker? Unverifiable without a live server, and the whole phase hinges on it. If only the At-variant arrives, guard #1 and the event choice invert. **Verify this first on a live server before writing the listener**; it is a 5-minute check that de-risks the entire phase.
