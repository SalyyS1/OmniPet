---
phase: 4
type: decision
created: 2026-08-03
---

# Phase 4 report: pet click interaction

## The blocking precondition — resolved statically, not deferred

The phase file marked one item BLOCKING: does a Paper `Interaction` entity deliver
`PlayerInteractEntityEvent`, or only `PlayerInteractAtEntityEvent`? The phase said this was
unverifiable without a live server. It is verifiable from the API jar, and the answer settles the
design:

```
$ javap org/bukkit/event/player/PlayerInteractAtEntityEvent
public class PlayerInteractAtEntityEvent extends PlayerInteractEntityEvent {
  public event.HandlerList getHandlers();
  public static event.HandlerList getHandlerList();
```

`PlayerInteractAtEntityEvent` **overrides `getHandlers()` with its own `HandlerList`**, confirmed
identical on both compatibility targets (`1.21`, `1.21.1`). Bukkit dispatches to the handler list the
event instance returns, so:

- A handler registered for `PlayerInteractEntityEvent` **never** receives an At-variant instance.
- Registering both would therefore deliver **two** events for one right-click, not one.

The plan's conclusion (register only the plain event) is correct, and its stated mechanism is correct
too. What remains genuinely live-only is whether Paper raises the At-variant *instead of* the plain
event for an `Interaction` entity specifically. If it did, the feature would be silently inert — it
would never misfire, never double-open, and never cancel another plugin's event. That failure mode is
safe, so it does not block the commit; it stays on the Phase 6 smoke list as a functional check.

## Index-leak decision (Phase 4 step 6) — wire nothing, filter instead

`InteractionIndex.removeOwner` (`:38`) is dead code: `grep` finds no caller anywhere.
`PetActivationService.remove` (`:137-147`) calls `interactions.unregister` **inside** the try block, so
a renderer whose `remove()` throws returns `false` and leaves the index entry behind. The entry is a
real leak.

**Decision: do not wire `removeOwner`, and do not treat entity-UUID non-reuse as sufficient.**

Reasoning:

1. Wiring `removeOwner` into owner-quit would shrink the leak but not close it. The per-pet path — a
   renderer that throws on `remove()` while the owner stays online — is the actual leak and
   `removeOwner` does not touch it.
2. "Entity UUIDs are never reused" is true but is the wrong guarantee to rely on. It means a stale
   entry can never point at a *different* entity, but the stale entry still names a pet that is no
   longer rendered, so a click on a leftover entity would open a management screen for a recalled pet.
3. `PaperPetRuntimeCoordinator.petFor` therefore filters on liveness rather than trusting the index:
   an identity resolves only when `activeRenderers(ownerId)` still holds that pet at that
   `rendererGeneration`. A stale entry answers empty, so the listener treats the click as not-ours and
   returns without cancelling.

This turns the leak from a correctness problem into a bounded-memory one. The remaining cost is a
`HashMap` entry per pet whose renderer threw during removal, which is already an error path that logs.
Closing it properly means moving `unregister` out of the failing try block in
`PetActivationService.remove`, which is a core-runtime change outside this plan's scope — it does not
touch escrow, journal, or reservation semantics, but it does change activation convergence behavior and
belongs in its own change with its own tests.

**Recorded as accepted with mitigation, not as accepted without.**

## What was built

| Change | File |
| --- | --- |
| Retain the already-constructed index instead of discarding it | `runtime/PaperRuntimeBootstrap.java` |
| `petFor(UUID)` — delegation plus liveness filter, no new state | `runtime/PaperPetRuntimeCoordinator.java` |
| `activeRenderers(ownerId)` exposure for the liveness check | `runtime/PaperRuntimeOwnerEngine.java` |
| The listener, hand filter first | `gui/pet/PetInteractListener.java` |
| Registration beside the other GUI listeners | `OmniPetPlugin.java` |

Not created, as the phase required: `PetEntityRef`, `PetEntityIndex`. A test asserts neither exists.

## Guard order, and why

1. `getHand() != EquipmentSlot.HAND` → return. The event fires **once per hand**; without this a single
   click opens twice and the second open trips `lifecycle.beginOpen`, so the player reads "Another
   management request is already processing." on every legitimate click. Tested per-hand, since a burst
   test cannot see it.
2. Lookup empty → return **without cancelling**, so another plugin's entity is untouched.
3. Ours → `setCancelled(true)`, suppressing the vanilla interaction.
4. Owner mismatch → return. Defense in depth only: `PetManagementMenuController.open` passes
   `(playerId, playerId, petId)`, so another player's pet is structurally unreachable. Asserted as a
   test rather than trusted.

Staleness moved out of the listener into `petFor`, where the live renderer list actually is. The phase
file put it in the listener via a generation supplier; that would have needed a fabricated "current
generation" with no real source, since generation is per-pet, not global.

## Pre-existing message conflict — kept, as the phase decided

`lifecycle.beginOpen` sends "Another management request is already processing." on a concurrent open.
That fires on *concurrency*, not on *ownership*, so it leaks nothing about whose pet an entity is.
Suppressing it would mean special-casing a shipped, tested path for no security gain. Kept.

## Verification

`gradlew.bat build` — BUILD SUCCESSFUL. Tests: 256 core + 415 paper = **671**, 0 failures, 0 skips
(baseline 605). All four guard tasks green; `omnipet-core` gained nothing.

## Unresolved

- Whether Paper raises `PlayerInteractEntityEvent` for an `Interaction` entity that is a passenger of
  an invisible marker. Fails safe (feature inert) if not. Phase 6 smoke item.
- Whether to move `interactions.unregister` out of the failing try block in
  `PetActivationService.remove`. Recommended as a separate core change; the liveness filter makes it
  non-urgent.
