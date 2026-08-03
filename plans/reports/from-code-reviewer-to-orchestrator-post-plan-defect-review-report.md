# Post-Plan Defect Review — OmniPet (HEAD~6..HEAD, weighted on `061a2a7`)

Scope: `061a2a7` (egg mint/grant), `9b5fd4c` (SoundResolver), `dca50e5`/`3c4e0c7` (vault tests) plus
immediate collaborators. Advisory only; no code changed. Config-surface issues already fixed in
`d57c37c`/`476e9a7` are excluded.

---

## 1. CRITICAL — The auto-created companion egg cannot hatch for a Studio pet that has no rarity bands (the default)

`EggAdminController.createCompanionEgg` / `envelope` — `EggAdminController.java:143-149`, `:250-259`
Failure surface: `PetIncubationProfileReader.read` — `PetIncubationProfileReader.java:20`, `:70-76`

The commit's stated goal is "a Studio-created pet is reachable". It is not, for the most common case.

A new Studio draft starts with **no rarity bands** (`PetStudioController.java:491` →
`StudioPetDraft.create(...)` passes `List.of()` for `rarityBands`), and `PetDefinitionStudioService`
deliberately permits that on save (`PetDefinitionStudioService.java:317`:
`if (!draft.rarityBands().isEmpty() && totalWeight <= 0)` — empty passes). `generatedRawNode`
(`:362-384`) copies the draft's `rawNode` and injects only `schemaVersion`, `definitionId`,
`revision`, `classification`, `icon`, `display` — it never synthesises a `rarity` node.

Hatch start then reaches `PetIncubationProfileReader.read`, which does
`rarity(nested(raw, "rarity", "bands"))`. `nested` calls `objectMap(source.get("rarity"), "rarity")`,
and `objectMap` throws on a non-Map (`:70-76`):

```java
if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " must be a map");
```

`raw.get("rarity")` is `null` → `IllegalArgumentException: rarity must be a map`.
(Even `rarity: {bands: []}` fails: `PetIncubationProfile.java:18` rejects an empty band list.)

Reproduction, exactly the path documented in `docs/getting-started.md:65-75`:

1. `/pet admin browse` → create pet `ember_fox`, set icon, **do not touch rarity bands** → save.
2. Studio auto-writes `eggs/ember_fox_egg.yml` and tells the operator to hand it out.
3. `/pet admin egg give <player> ember_fox_egg` → egg is minted and delivered (valid PDC identity).
4. `/pet hatch main` → player is told `HATCH_START_QUEUED` (`PlayerHatchController` line ~177).
5. Async: `PaperIncubationStartSaga.prepareAndStart` (`:92-134`) → `hatches.start` → `rolls.roll` →
   `validateCandidates` → `profileReader.read` throws. Caught at `:128`, logged as
   "incubation start is pending", recovery requested.

Outcome: the player is told the hatch was queued and **nothing ever happens**. Escrow is left at
`PREPARED`; recovery correctly sees the item still present and cancels (`EggEscrowRecoveryService`
`prepared(...)` → `CANCEL_TRANSACTION`), so no item is lost — but the pet is unobtainable and the
only signal is a server-log line. The gap this commit was written to close remains open.

Fix direction (either is sufficient, both are cheap):
- `envelope()` should refuse to write a companion egg for a definition whose `rawNode` has no usable
  `rarity.bands`, and `createCompanionEgg` should report that to the operator (`PetStudioController`
  already has the message path). Fail loudly at save, not silently at hatch.
- Or make `PetIncubationProfileReader.rarity` treat an absent `rarity` node as a single implicit
  full-range band, so a pet with no authored rarity profile is hatchable by definition.

Note the test suite does not catch this: `EggCompanionCreationTest` builds `PetDefinition` with
`rawNode = Map.of()` (`EggCompanionCreationTest.java:139-146`) and only asserts the egg *file*
contents. It never drives the egg back through `HatchService.start`, which is where the mismatch
lives — a phantom-coverage gap on the one property the commit message claims ("an egg is accepted by
capture() by construction" is proven; "the pet is reachable" is not).

---

## 2. HIGH — `/pet admin pet give` performs repository I/O on the Paper main thread

`EggAdminController.petCommand` → `grant` — `EggAdminController.java:119-134`, `:197-208`

`petCommand` asserts it is on the main thread (`requireMainThread()`, `:121`) and then calls
`grant`, which does two blocking repository round-trips on that thread:

```java
var snapshot = storage.snapshot(playerId, resolved);                                  // :200
PetStorageResult result = storage.admit(playerId, snapshot.revision(), pet(...), ...); // :201
```

Both go through `FilePlayerStateRepository` (`:37-58`), which acquires
`SharedRepositoryLockRegistry.acquire(...)` — a `ReentrantLock` **plus** an OS file lock whose
retry loop is `LockSupport.parkNanos(1_000_000L)` in an unbounded `while (true)`
(`SharedRepositoryLockRegistry.java:53-65`), then reads and `fsync`s YAML.

This contradicts the project's own stated invariant (`docs/developer-guide.md:66`): "Repository work
runs on Paper async workers; Bukkit inventory, permission, and provider work stays on the main
thread." Every peer admin controller obeys it — `HatchAdminController` submits through
`PerPlayerTaskQueue` and hops back with `mainDispatcher` (`HatchAdminController.java:107-125`);
`SlotTransactionAdminController.java:175` uses `runTaskAsynchronously`. This is the only
`RepositoryPetStorageService.admit` call site in the Paper module, and the only one off-queue.

Concrete failure: target player has a vault toggle in flight on their `PerPlayerTaskQueue` worker
(that worker holds the player's file lock). An operator runs
`/pet admin pet give <target> <definitionId>`. The main thread enters `acquire`, finds the lock held,
and parks in 1 ms increments until the async worker completes its read-mutate-fsync-move cycle. On a
slow or contended disk that is a multi-tick server stall for every online player. Under
`AtomicFileStore.write` (temp file + backup copy + fsync + two atomic moves, `:12-35`) the window is
not small.

---

## 3. HIGH — `StaleRevisionException` from the grant path escapes into Brigadier

`EggAdminController.petCommand` — `EggAdminController.java:127-133` (with `grant`, `:200-201`)

`grant` reads the revision and then mutates under it as two separate lock acquisitions. There is no
atomicity between them and, because #2 bypasses the per-player queue, no serialisation either:

```java
var snapshot = storage.snapshot(playerId, resolved);          // revision N observed, lock released
PetStorageResult result = storage.admit(playerId, snapshot.revision(), ...); // lock re-acquired
```

If anything bumps the revision in between — a queued vault activate/deactivate, a hatch tick, a
slot purchase, join-time `reconcileLimits` — `FilePlayerStateRepository.withLocked` throws
`StaleRevisionException` (`:48`). `RepositoryPetStorageService.mutate` only catches its own private
`StorageRejected` (`:89-91`), so the exception propagates.

`petCommand`'s handler is:

```java
} catch (IOException | IllegalArgumentException failure) {
```

`StaleRevisionException extends IllegalStateException` — **not** caught. It unwinds through
`OmniPetCommand.execute` (`:407-417`) into the Brigadier dispatcher.

Concrete failure: operator runs `/pet admin pet give Steve ember_fox` at the moment Steve clicks a
vault toggle. The pet is not granted, the operator gets a raw internal command error (stack trace
surface / "An internal error occurred", depending on the dispatcher) instead of the intended
"pet not granted - ..." line, and the failure looks like a plugin crash rather than a retryable
conflict. Every other repository caller in the codebase handles this explicitly and retries —
`PlayerPetController.reconcileAsync` retries twice, `PlayerPetController.toggle` catches it and
re-opens the vault, `ReleaseOutboxStateUpdater` retries at `:47`, `:84`, `:105`.

Fix: move `grant` onto the shared `PerPlayerTaskQueue` (which resolves #2 as well), and
snapshot-then-admit with a bounded retry on `StaleRevisionException` like `reconcileAsync` does.

---

## 4. MEDIUM — An admin-granted pet's `rarityId` is a tier letter, so it is scored as the weakest rarity

`EggAdminController.pet` — `EggAdminController.java:218-232`, specifically `:221`

```java
hatching.put("rarityId", definition.tier().name());   // "D" | "C" | "B" | "A" | "S"
```

The method's Javadoc claims it is "Shaped to match what `IncubationPetFactory` writes … so a granted
pet renders and cultivates identically." It is not. `IncubationPetFactory.java:20` writes
`outcome.rarityId()`, which is a **rarity band ID** drawn from the pet's own `rarity.bands`
(`DeterministicHatchRollService.java:33-34,51` → `HatchRarityBand.id()`), a distinct namespace from
`PetTier`. Two consumers read the field and both misbehave:

- `PetManagementMenuSupport.progressionContext` (`:46-47`) → `rarityValue(name)` (`:78-87`) switches
  on `COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC` with `default -> 1`. `"S"` matches nothing, so an
  S-tier admin-granted pet feeds `rarity = 1.0` into the experience formula. Concrete: with the
  shipped sample set (`OmniPetConfigLoader.java:129` seeds `rarity: 1.0`) and any formula that scales
  on `rarity`, that pet levels at common-pet cost — cheaper than an equivalently-tiered hatched pet
  whose band is `legendary` (5.0). "Cultivates identically" is false in the direction that matters.
- `VaultSortOrder.RARITY_DESC` (`VaultSortOrder.java:39-48`) sorts rarity IDs reverse-alphabetically.
  Granted pets ("S", "B", "D") interleave arbitrarily with hatched pets ("legendary", "common") —
  `"legendary"` > `"S"` > `"D"` > `"common"`, which is meaningless to a player.

Either write a real band ID from the definition's own `rarity.bands`, or omit `rarityId` entirely for
an admin grant. Omitting is honest and already handled: `VaultPetSummary.readRarity` (`:76-79`)
returns null for an absent field and `PetManagementMenuSupport` skips the key. `source:
"admin-grant"` already records the provenance; a fabricated rarity adds nothing but wrong arithmetic.

Note this interacts with #1: a pet with no authored rarity bands has no valid band ID to write, which
is the same missing invariant surfacing in a second place.

---

## 5. MEDIUM — A companion egg is never re-tiered after a Studio tier change, permanently breaking its hatch

`EggAdminController.createCompanionEgg` — `EggAdminController.java:143-149`
Failure surface: `DeterministicHatchRollService.validateCandidates` — `:72-74`

`createCompanionEgg` returns early when a catalog entry exists, and `envelope()` stamps
`definition.tier()` onto the egg at creation time. `DeterministicHatchRollService` requires them to
agree:

```java
if (definition.tier() != egg.tier()) {
    throw new IllegalArgumentException("egg candidate tier does not match egg tier: " + ...);
}
```

Reproduction:

1. Create `ember_fox` at tier D in the Studio → save → `ember_fox_egg` written with `tier: D`.
2. Reopen it, cycle tier to S (`PetStudioController.editTier`, `:375-380`) → save.
3. `createCompanionEgg` sees `ember_fox_egg` already present → returns empty → egg keeps `tier: D`.
   The operator gets **no message at all** (the `ifPresent` at `PetStudioController.java:407` only
   fires on a write).
4. Any player holding or later given that egg: `/pet hatch main` → tier mismatch thrown → same silent
   dead end as #1 (queued message, nothing happens, operator log only).

The "never overwrite an operator's tuning" rule is right, but tier is not tuning — it is a
consistency constraint the hatch roller enforces. At minimum `createCompanionEgg` should compare the
existing egg's tier to the definition's and warn the operator that the egg is now unhatchable and
needs `/pet admin egg create`. Silently returning empty is the worst of the three options.

---

## 6. LOW — Collided declaration line in `OmniPetCommandTree`

`OmniPetCommandTree.java:133`

```java
private static CommandSpec.Builder adminHatch() {        return CommandSpec.of("hatch", ...)
```

Same class of formatting collision as the `OmniPetPlugin.loadConfig` one already fixed in
`d57c37c`/`476e9a7`; introduced by the `adminEgg()`/`adminPet()` insertion in `061a2a7`. Compiles and
behaves correctly — reported only because it is the residue of the same mechanical edit and is
trivially fixed alongside it. Also note the import block at `OmniPetPlugin.java:53-56` now interleaves
`paper.incubation.*` between two `paper.gui.*` imports.

---

## Verified as NOT defects (checked, discarding)

- `PaperEggItemCodec.sign`/`create` (`:144-171`) write exactly the three keys and `ITEM_SCHEMA` that
  `observe` (`:81-109`) validates and `capture` (`:55-79`) accepts. Mint→observe→capture is sound;
  amount is forced to 1 so no two eggs share a nonce.
- `SoundResolver` (`:44-71`) avoids the `IncompatibleClassChangeError` correctly: `Registry.SOUNDS.get`
  plus a key-direction scan, and `constantName(Keyed)` emits `invokeinterface`. `FeedbackSettings`
  only holds `Sound` as a type reference, never a `Methodref`, so the new contract test's invariant
  holds for real.
- `EggAdminController.emptySlots` (`:267-275`) is correct after the `d57c37c` fix: `getStorageContents()`
  length is 36 for a `PlayerInventory` and `getItem(0..35)` addresses the same storage slots.
- `give` (`:156-180`) is fail-closed: it collects free slots first and delivers nothing when there are
  too few, so a partial hand-out cannot occur. `NumberFormatException` from `positive` is an
  `IllegalArgumentException` and is caught.
- `YamlEggDefinitionRepository.save` (`:41-56`) checks the size bound *before* writing and goes through
  `AtomicFileStore`, so an oversized definition never lands in a state `read` would refuse.
- Studio hook placement (`PetStudioController.java:382-389`) genuinely cannot roll back or obscure the
  definition write, and its own `catch (IOException | RuntimeException)` (`:409`) prevents an egg
  failure from being misreported as a save rejection.
- `GuiConfig`'s new record component has exactly two construction sites, both updated;
  `GuiConfigLoader.encode` (`:73-79`) round-trips it, so no migration drop.

---

## Unverified — needs follow-up

None. Every item above was read against the surrounding code; nothing was left as a hunch.

---

## Recommended actions (priority order)

1. Fix #1 — decide where the missing `rarity.bands` invariant belongs (refuse the egg at save, or
   default the band in the reader) and add a test that drives a *Studio-shaped* definition through
   `HatchService.start`, not just through the egg codec.
2. Fix #3 with #2 — move `grant` onto `PerPlayerTaskQueue`, hop to main only for the player message,
   and retry on `StaleRevisionException`.
3. Fix #4 — stop fabricating `rarityId` from `PetTier`.
4. Fix #5 — warn the operator when an existing companion egg's tier no longer matches its pet.
5. Fix #6 — cosmetic, bundle with any of the above.
