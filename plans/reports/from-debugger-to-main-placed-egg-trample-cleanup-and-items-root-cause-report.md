# Placed eggs: trample loss, leftover block, unusable support items — root cause report

Date: 2026-08-05. Read-only investigation; no code changed, no build run.

Scope: `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/incubation/placed/*` plus
`OmniPetPlugin.hatchPlacedEgg`, `IncubationActionItemController`.

## Evidence base

Material of a placed egg block: the egg ITEM's material, default `TURTLE_EGG`.

- `incubation/EggAdminController.java:58` — `DEFAULT_EGG_MATERIAL = Material.TURTLE_EGG`
- `incubation/EggAdminController.java:405-411` — item minted with
  `ItemAppearanceApplier.material(appearance, DEFAULT_EGG_MATERIAL, "items.egg", ...)`
- `omnipet-paper/src/main/resources/config.yml:190-191` — operators may override to `DRAGON_EGG`
  (commented example)

So placing the item yields a vanilla `TURTLE_EGG` block (or whatever `items.egg.material` says).
Nothing in the placed package inspects or asserts the block material — the only `getType()` call in
the whole package is on NEIGHBOURS for the heat requirement (`PlacedEggCoordinator.java:267`).

Events handled by `PlacedEggListener`:

- `PlacedEggListener.java:45` `onPlace(BlockPlaceEvent)`
- `PlacedEggListener.java:75` `onBreak(BlockBreakEvent)`
- imports confirm only those two: `PlacedEggListener.java:11-12`

Repo-wide grep for `EntityChangeBlockEvent|BlockPhysicsEvent|EntityExplodeEvent|BlockExplodeEvent|
EntityInteractEvent|BlockFromToEvent|BlockPistonExtendEvent|BlockFadeEvent|PlayerInteractEvent`
returns **no matches anywhere in omnipet-paper**. None of these are handled by any listener.

---

## BUG E — stepping on a placed egg destroys it

### Root cause

The placed egg is a vanilla `TURTLE_EGG` block (`EggAdminController.java:58`). Vanilla removes a
turtle egg when an entity walks/jumps/falls on it, and turtle eggs also pop when their support block
goes away, when pushed by a piston, or when hit by an explosion. `PlacedEggListener` only listens to
`BlockPlaceEvent` and `BlockBreakEvent` (`PlacedEggListener.java:45,75`), so **every non-player-mining
removal path is unguarded**. DRAGON_EGG (the documented alternative material) is worse in a different
way: punching it teleports it, which moves the block away from the record's coordinates with no event
the plugin listens to.

Trample-relevant events that exist and are not handled: `PlayerInteractEvent` with
`Action.PHYSICAL`, `EntityInteractEvent` (mobs), and `EntityChangeBlockEvent` /
`BlockPhysicsEvent` / `BlockFromToEvent` / explosion + piston events for the other removal paths.

### Consequence: the record is orphaned, and the egg can be permanently lost

Record deletion happens in exactly two places:

- `PlacedEggCoordinator.reclaim(...)` — `PlacedEggCoordinator.java:133-142`, called only from
  `PlacedEggListener.onBreak` (`PlacedEggListener.java:81`)
- `PlacedEggCoordinator.consume(...)` — `PlacedEggCoordinator.java:223-233`, called only after a
  successful pet grant (`OmniPetPlugin.java:457` via `PlacedEggServices.java:65-68`)

Nothing else. There is no reconciliation pass that checks "does a block still exist at this record's
coordinates, and is it still the egg material". `PlacedEggView.pass()` only skips a record when the
**world or chunk** is missing (`PlacedEggView.java:189-192`); an AIR block ticks normally.

So after a trample:

1. block gone, record still on disk in `data/placed-eggs/<world>_x_y_z.yml`
2. countdown keeps running (heat neighbours are unchanged, so `PlacementRequirement.satisfiedBy`
   still passes — `PlacedEggCoordinator.java:159`)
3. hologram keeps floating over empty air (`PlacedEggView.refresh`, line 108-113)
4. the player can no longer break the block, so `reclaim` is unreachable — the egg cannot be taken
   back at all
5. re-placing another egg at the same coordinates is refused: `store.read(key).isPresent()` →
   "another egg is already incubating there" (`PlacedEggCoordinator.java:93-95`). The location is
   bricked.

Whether the egg is *lost* then depends on the ready path, and there it can be lost outright — see
the ready-path defect below, which is shared with Bug F.

### Ready-path defect (amplifies E, and is the loss mechanism)

`PlacedEggCoordinator.tick` returns `Optional.empty()` immediately for an already-ready record:

    PlacedEggCoordinator.java:153
    if (elapsedMillis <= 0 || record.ready()) return Optional.empty();

and `onReady` is only invoked from inside that `ifPresent`:

    PlacedEggView.java:193-196
    coordinator.tick(block, record, elapsedMillis).ifPresent(advanced -> {
        refresh(advanced);
        if (advanced.ready()) onReady.accept(advanced.ownerId(), advanced);
    });

Therefore `onReady` fires **exactly once, on the single pass that crosses zero**. But
`hatchPlacedEgg` bails out when the owner is offline:

    OmniPetPlugin.java:442-443
    var owner = getServer().getPlayer(ownerId);
    if (owner == null || !owner.isOnline()) return;

and also when the vault is full (`OmniPetPlugin.java:453-455`) or when the definition cannot be
resolved (`OmniPetPlugin.java:448-449`). In every one of those cases the one-shot chance is spent and
never retried — the record sits at `remaining=0` forever. The doc comment at
`OmniPetPlugin.java:435-439` ("the reward waits for the player") and the hologram text
"Ready! Break to claim" (`text/MessageKey.java:49`) both describe behaviour the code does not have:
the only remaining recovery is *breaking the block to get the egg item back*, which returns the egg,
not the pet.

Combine with a trample: the block is gone, so break-to-reclaim is impossible, and the ready retry
never happens. **The egg is silently and permanently lost.** Highest severity finding in this report.

### Severity

- **CRITICAL — silent permanent loss of player property** when trample + (owner offline OR vault
  full OR unresolved definition) at the moment of ready.
- **HIGH** otherwise: trample alone makes the egg unreclaimable until ready, leaks a hologram over
  air, and bricks the coordinates against re-placement.
- Bug vs unimplemented: **bug** (missing event guards + missing record/block reconciliation).

---

## BUG F — block left behind when the timer finishes

### Root cause

No code path ever sets the placed egg's block to AIR. Grep for `setType` across
`incubation/placed/` returns nothing; the only `getType()` is the neighbour scan at
`PlacedEggCoordinator.java:267`.

The completion path is:

1. `PlacedEggView.pass()` → `onReady.accept(...)` — `PlacedEggView.java:195`
2. `OmniPetPlugin.hatchPlacedEgg(...)` — `OmniPetPlugin.java:441`, grants the pet asynchronously
3. on success → `placedEggs.consume(record)` — `OmniPetPlugin.java:457`
4. `PlacedEggServices.consume` — `PlacedEggServices.java:65-68` → `view.remove(key)` +
   `view.consume(record)`
5. `PlacedEggView.consume` — `PlacedEggView.java:172-175` → `coordinator.consume(record)` +
   `holograms.hide(record.key())`
6. `PlacedEggCoordinator.consume` — `PlacedEggCoordinator.java:223-233` → `store.delete(key)` +
   `forget(key)`

Steps 4-6 delete the YAML record and remove the hologram. **The world block is never touched.**
That is exactly the reported symptom: record gone, block survives.

The method that should clear the block and does not: `PlacedEggView.consume(PlacedEggRecord)`
(`PlacedEggView.java:172-175`) — it is the only step in the chain that still has main-thread context
and can resolve the block via the existing private `block(record)` helper
(`PlacedEggView.java:200-204`). `PlacedEggCoordinator.consume` is Bukkit-block-agnostic by design, so
the view is the right seam.

### Secondary consequence of the leftover block

The leftover `TURTLE_EGG` is now a plain vanilla block with no record. Breaking it drops a vanilla
turtle egg item (`onBreak` returns early at `PlacedEggListener.java:78` when no record exists, so
`setDropItems(false)` is not applied) — a small vanilla-item duplication/pollution, not a pet dupe.

### Severity

- **HIGH (cosmetic/world-state, no property loss)**: no egg or pet is lost by F alone; the world
  accumulates orphan egg blocks and a stray vanilla drop.
- Bug vs unimplemented: **bug** (missing one block-clear step in an otherwise complete path).

---

## BUG G — reducer / instant-hatch items do not work on placed eggs

### Root cause: the feature was never implemented for placed eggs

Redemption today targets the player profile's single in-progress incubation only:

    IncubationActionItemController.java:69-99  redeem(Player, EggInventoryHand)
    IncubationActionItemController.java:73-77
        var state = hatches.snapshot(player.getUniqueId());
        if (state.incubation() == null) { HATCH_ITEM_NO_INCUBATION; return; }
    IncubationActionItemController.java:80-88
        new IncubationItemActionTransaction(token, playerId,
            state.incubation().id(), state.revision(), ...)

The transaction is keyed by `state.incubation().id()` and `state.revision()` — the held-egg
incubation aggregate. A `PlacedEggRecord` has no id of that kind and never enters this path.

Entry points, all of which route to that same method:

- `/pet hatch use-main` / `use-off` — `command/OmniPetCommandTree.java:56-57`,
  `command/PlayerCommandRouter.java:64`
- hatch GUI buttons `REDEEM_MAIN` / `REDEEM_OFF_HAND` —
  `gui/hatch/HatchInventoryHolder.java:59-65`
- operator distribution `/... reducer` / `/... instant` —
  `command/OmniPetCommandTree.java:99-102`, `IncubationActionItemController.java:101`

Missing plumbing on the placed-egg side:

- `PlacedEggRecord` has no reduction/apply API. Only `withRemaining(long)`
  (`PlacedEggRecord.java:68-72`) exists, and it is called from `tick` and tests only.
- `PlacedEggCoordinator` exposes `place / at / reclaim / tick / flush / consume / all` — no
  "apply reduction" method (`PlacedEggCoordinator.java:76,113,133,152,177,223,247`).
- `PlacedEggListener` handles no right-click at all: only `BlockPlaceEvent` and `BlockBreakEvent`
  (`PlacedEggListener.java:11-12,45,75`). There is no `PlayerInteractEvent` handler in the whole
  plugin, so clicking a placed egg does nothing and no GUI can open.

### Severity

- **MEDIUM — unimplemented feature**, not a broken wire. No data loss. The user's suggestion
  (right-click a placed egg → GUI that accepts support items) is a new feature: it needs a
  `PlayerInteractEvent` handler, a reduction method on the coordinator that mutates
  `remainingMillis` and flushes, and a placed-egg-aware variant of the redemption transaction (or a
  deliberate decision to bypass the escrow-style transaction, since a placed egg is explicitly
  outside escrow per `PlacedEggRecord.java:9-14`).

---

## Hypotheses considered and eliminated

- *E is a heat-requirement re-check evicting the egg.* Eliminated: `tick` merely returns empty when
  the requirement fails (`PlacedEggCoordinator.java:159`) — it pauses, never deletes or breaks.
- *E is the plugin's own break handler firing on a physics event.* Eliminated: `onBreak` is bound to
  `BlockBreakEvent` only (`PlacedEggListener.java:74-75`), which is not fired by trampling; and
  grep shows no other block-event listener exists.
- *F is a failed `consume` leaving both record and block.* Eliminated: `consume` deletes the record
  and force-`forget`s even on IO failure (`PlacedEggCoordinator.java:223-233`); the record does go,
  which matches the report (block remains, timer/hologram gone).
- *G is a wired path failing a guard.* Eliminated: the transaction is constructed from
  `state.incubation()` and there is no placed-egg branch anywhere
  (`IncubationActionItemController.java:73-88`); no code references both a reducer and a
  `PlacedEggRecord`.
- *Holograms leak on trample because they are persisted.* Eliminated: displays are
  `setPersistent(false)` and rebuilt from records (`PlacedEggHolograms.java:60`), so the leak is a
  consequence of the orphaned record, not of hologram lifecycle.

## Recurrence prevention (design/monitoring gaps, not fixes)

1. No invariant check that a record's coordinates still hold the egg block. A startup + periodic
   reconciliation would have turned Bug E from silent loss into a logged, recoverable state.
2. `onReady` is a one-shot edge trigger over a persistent `ready` state. A ready record that failed
   to grant is invisible: there is no warning log and no retry. A level-triggered design (re-attempt
   ready records each pass, or a dedicated ready queue) removes the whole class.
3. The doc comment at `OmniPetPlugin.java:435-439` and `MessageKey.EGG_PLACED_VAULT_FULL` assert a
   retry that does not exist — comments were not validated against behaviour.
4. No metric/log for orphan `data/placed-eggs/*.yml` count; the only scan warning covers unreadable
   or truncated files (`PlacedEggView.java:86-93`).

## Unresolved questions

1. Which exact Bukkit event(s) does the target Paper version fire when a **player** tramples a
   turtle egg (`PlayerInteractEvent` PHYSICAL vs `EntityChangeBlockEvent` vs neither)? Needs a live
   server probe; I did not run the server. Mobs/falling entities are `EntityInteractEvent` /
   `EntityChangeBlockEvent` territory. This changes which guard is sufficient.
2. Is switching `items.egg.material` away from TURTLE_EGG (e.g. to a non-fragile block) an
   acceptable mitigation, or is the turtle-egg look a product requirement? DRAGON_EGG is not a fix —
   it teleports on punch.
3. On completion, should the block become AIR or should the placed egg drop nothing and also spawn a
   hatch effect? Only the removal is reported; visual intent is a product call.
4. For Bug G, should applying a reducer to a placed egg go through
   `IncubationItemActionTransaction` (escrow-adjacent, per-incubation revision) or a simpler direct
   mutation on the record? `PlacedEggRecord.java:9-14` argues explicitly for keeping placed eggs out
   of escrow code.
5. Should a ready-but-ungrantable placed egg keep its block (so break-to-reclaim stays available) or
   retry the grant on owner join? These two answers interact with the Bug F fix.
