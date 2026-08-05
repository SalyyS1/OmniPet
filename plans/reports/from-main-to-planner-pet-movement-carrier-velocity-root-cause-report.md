# Pet movement: why the carrier teleports instead of following

Investigation of the report: *"pet head bị update siêu chậm và không di chuyển theo player (chỉ dịch chuyển đến vị trí player khi player quá xa, rồi đứng im tại vị trí đó và lặp lại)"*

## Verdict

The core steering is **correct**. The bug is entirely in the Paper renderer: it moves the carrier with
`setVelocity` on a **marker armor stand with gravity disabled**, and that combination does not produce
sustained motion. The only movement the player ever sees is the hard teleport that fires when the gap
exceeds `safetyDistance`, which is exactly the reported symptom.

## Evidence

### The core loop is healthy

Simulated `MovementController.step` with the shipped `MovementProfile.defaults()` against an owner
walking 4.3 m/s:

```
t=0.50s owner=2.37 pet=-0.35 gap=2.72 petSpeed=1.67
t=1.00s owner=4.51 pet=1.29  gap=3.23 petSpeed=4.02
t=1.50s owner=6.66 pet=3.40  gap=3.26 petSpeed=4.30
t=3.00s owner=13.11 pet=9.86 gap=3.26 petSpeed=4.30
```

The spring converges and holds a steady 3.26 block gap at owner speed. `MovementController`,
`MovementProfile`, and the coordinator's per-tick `advance` are not at fault. The transform handed to the
renderer each tick carries a correct, continuously-updated position.

### The renderer discards it

`BukkitPaperHeadRendererBackend.smoothMove` (`BukkitPaperHeadRendererBackend.java:102-120`) converts the
target into a velocity and calls `setVelocity`:

```java
Vector velocity = new Vector(
        transform.position().x() - current.getX(), ...).multiply(settings.movementGain());
...
nativeCarrier.setVelocity(velocity);
```

The carrier is spawned at `BukkitPaperHeadRendererBackend.java:44-52` as:

```java
stand.setMarker(true);
stand.setGravity(false);
```

Two problems compound:

1. **`setVelocity` is not a position command.** It sets velocity for the entity's own physics tick. A
   gravity-disabled marker armor stand has effectively no movement physics applied, so the velocity is
   either ignored outright or decays to nothing before it displaces the entity meaningfully. Nothing
   integrates it into a position the way the core loop integrates its own velocity.
2. **The units disagree by 20×.** The core produces metres per second; `setVelocity` takes blocks per
   tick. `movementGain: 0.35` against a 3.26 block gap asks for 1.141 blocks/tick — 22.8 m/s — which is
   then clamped by `maximumVelocity: 1.2`. So even where velocity *is* honoured the numbers are
   accidental rather than derived, and the clamp is doing load-bearing work it was not designed for.

### Why it looks like a teleport loop

`PaperHeadMovementPolicy.decide` (`PaperHeadMovementPolicy.java:7-19`) returns `HARD_SAFETY_DISTANCE` once
`distanceSquared > safetyDistance²`, with `safetyDistance: 8.0`. The sequence the player sees is
therefore: carrier does not move → owner walks away → gap passes 8 blocks → hard teleport → carrier sits
still again → repeat. That is the report verbatim.

### The at-rest guard makes it worse, not better

`smoothMove` returns early when the requested velocity is below `AT_REST_VELOCITY_SQUARED` (1e-6) *and*
the carrier's current velocity is below it too (`BukkitPaperHeadRendererBackend.java:115-117`). For a
carrier whose velocity is always ~0 because physics never applied it, the second half of that condition is
permanently true. The guard was added to stop redundant packets for a genuinely stationary pet; with a
carrier that cannot move under velocity it also suppresses writes for a pet that is merely stuck.

## The fix

Move the carrier by **teleport**, not velocity. `teleport` sets position directly and is what a
server-driven, physics-free carrier needs. The visual smoothness that `setVelocity` was reaching for is
already provided by the display entity's own interpolation — `setInterpolationDuration` and
`setTeleportDuration` are already being set at `BukkitPaperHeadRendererBackend.java:169-170`, and those
are what make a teleported display glide on the client rather than jump.

This means:

- `smoothMove` teleports the carrier to `transform.position()` and sets rotation.
- `movementGain` and `maximumVelocity` stop being movement parameters for the carrier. They are still
  read by `PaperModelEngineRenderer` and by the gait threshold, so they cannot simply be deleted — decide
  deliberately what each still means.
- The at-rest guard should compare **positions**, not velocities: skip the write when the carrier is
  already within a small epsilon of the target and facing the right way. That preserves the packet saving
  the guard was added for, and it works for a carrier that moves by teleport.
- `settings.interpolationTicks()` must be at least as long as the interval between updates, or the client
  finishes interpolating and stutters before the next teleport arrives. At `periodTicks: 1` the current
  value of 3 is fine.

## Related, and worth deciding at the same time

`PaperModelEngineRenderer.update` (`PaperModelEngineRenderer.java:~155-165`) uses the same
`setVelocity`-on-a-marker-stand approach, with the same two flags set at
`PaperModelEngineRenderer.java:105-106`. It has the same defect. A fix that only touches the HEAD renderer
leaves ModelEngine pets stuck.

`PaperHeadRendererSourceContractTest` guards the HEAD renderer's source, including that it does not drop
`setVelocity`. That test encodes the current approach as an invariant, so it has to be revised
deliberately as part of the fix rather than worked around.

## Unresolved questions

1. **Is a marker armor stand the right carrier at all?** A non-marker stand has a hitbox and physics; a
   marker has neither. If the carrier only ever needs to hold passengers and be teleported, `setMarker`
   is correct and velocity was always the wrong tool. Worth confirming against the live server rather
   than reasoning about it, since Paper's handling of marker-stand physics has changed across versions.
2. **What should `movementGain` mean after this?** It is an operator-facing config key. Repurposing it
   silently is worse than removing it with a migration warning.
3. Not verified on a live server. Everything above is read from source and simulation; the conclusion is
   strongly supported but the definitive test is watching one pet on Paper with a debug log of the
   decision the policy returns each tick.
