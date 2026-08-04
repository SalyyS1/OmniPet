# Hatch: egg consumption + GUI refresh — RCA

Read-only. All claims `file:line`. No build run.

## 0. Hatch-start order of operations (proven)

`/pet hatch main` → `OmniPetCommandTree.hatch()` leaf `main` (`omnipet-paper/.../command/OmniPetCommandTree.java:52`)
→ `PlayerHatchController.command` `case "main"` (`.../player/PlayerHatchController.java:100`)
→ `PlayerHatchController.start` (`:166`), guard `mutations.add` (`:169`), `coordinator.start` (`:176`), message `HATCH_START_QUEUED` (`:177`)
→ `PaperIncubationCoordinator.start` (`.../incubation/PaperIncubationCoordinator.java:76`)
→ `PaperIncubationStartSaga.start` (`.../PaperIncubationStartSaga.java:61`): capture egg identity on main thread (`:69`), then async `prepareAndStart` (`:70`).

`prepareAndStart` (`:92`) exact order:
1. read egg definition `:95`
2. snapshot `:97`, registry `:98`
3. build txn `PREPARED` `:99-107`, **`itemEscrow().prepare` → durable PREPARED record `:108`**
4. **`services.hatches().start(...)` `:114`** — this is where the roll runs: `RepositoryHatchService.start:45-55` → `withLocked` `:114` → `HatchService.start:31` → `rolls.roll(...)` `omnipet-core/.../HatchService.java:52` → `DeterministicHatchRollService.roll:26` → `validateCandidates:64` → `profileReader.read(definition)` `:75`.
5. only if `STARTED` → `runMain(... removeCapturedItem ...)` `:127`
6. `removeCapturedItem` `:145` → `inventoryEscrow.removeOne` `:151` — **egg leaves the inventory here**
7. `commitRemoved` `:170` → `markItemRemoved` → `ITEM_REMOVED` → `commit` → `COMMITTED` `:172-177`.

So: **roll (step 4) strictly precedes egg removal (step 6).** Egg removal is gated on `started.status() == STARTED` (`:122`).

---

## 1. Egg-consumption verdict — SAME bug as 7015835 for the Studio-pet case

Confirmed. Pre-7015835 `PetIncubationProfileReader.read` called `nested(raw,"rarity","bands")` → `objectMap(...,"rarity")` on a `null` node → `rarity must be a map` (removed lines in `git show 7015835 -- .../PetIncubationProfileReader.java`; new defaulting at the same file `:17-46` post-fix). The throw fires inside step 4, i.e. **before** step 6, and step 6 is unreachable because step 4 never returns `STARTED`. Therefore the egg was never touched. Player sees `HATCH_START_QUEUED` (`PlayerHatchController.java:177`) because `coordinator.start` only reports *queued*, not *succeeded* — the async failure is log-only (`PaperIncubationStartSaga.java:129`).

Durable leftover on that path: exactly one escrow file at `PREPARED` (step 3 completed). No incubation row: the `RuntimeException` propagates out of the mutation lambda inside `FilePlayerStateRepository.withLocked` (`omnipet-core/.../FilePlayerStateRepository.java:44-57`) **before** the `fileStore.write` at `:55`, so nothing is persisted.

Recovery then resolves that escrow cleanly: `requestRecovery` `:131` → `PaperIncubationRecoveryExecutor.recover` → `inspectAndApply:120` → `EggEscrowRecoveryService.inspect` `case PREPARED` `:31` → `prepared(...)`: item present, `viable == false` (no incubation) → **`CANCEL_TRANSACTION`** (`EggEscrowRecoveryService.java:83-84`) → `cancelTransaction:175`.

⇒ Post-7015835 this specific report is fixed. **No stuck state from it**; retry after the fix works. It is NOT the cause of report B.

## 1b. A SECOND, still-live egg-not-consumed path (different bug)

If step 4 succeeds (durable `INCUBATING`) but step 6 fails, escrow stays `PREPARED` and the egg stays in the inventory: `removeCapturedItem` returns on anything but `REMOVED` (`PaperIncubationStartSaga.java:159-164`), log-only.

`EggInventoryEscrowService.removeOne` (`omnipet-paper/.../EggInventoryEscrowService.java:26-39`) demands `inventory.handMatches(identity) && stack.slot() == identity.inventorySlot() && stack.amount() == expected`. `handMatches` compares against the **live** held-item slot (`PaperPlayerEggInventory.java:55-60`). The capture at `:69` and the removal at `:151` are separated by an async round trip, so a hotbar scroll or a stack move in that window yields `NOT_MATCHING`.

Recovery cannot heal it: `observe` matches on **nonce only**, not slot (`EggInventoryEscrowService.java:10-24`, `:56-67`), so it reports `MATCHING_ITEM_PRESENT`; with a viable incubation `prepared()` returns `REMOVE_MATCHING_ITEM` (`EggEscrowRecoveryService.java:80-82`) → `removeMatching:143` → the same slot-strict `removeOne` → `NOT_MATCHING` → silent return (`PaperIncubationRecoveryExecutor.java:154-156`). Retried forever, every 5 s (below), never resolving.

Net durable state: egg kept, incubation `INCUBATING`, escrow pinned at `PREPARED`.

---

## 2. Stuck state + the exact 'already' string

**Permanently stuck state exists** — but it is the 1b path, not the rarity path.

Consequences of `INCUBATING` + escrow `PREPARED`:
- **Countdown frozen.** The ticker refuses to advance unless escrow is `COMMITTED`: `PaperIncubationCoordinator.java:122-131` (`transaction.stage() != COMMITTED` → `reset` + `recover` + `return`). `hatches().tick` is never called, so `remainingActiveMillis` never changes → GUI redraws identical text forever.
- **No refresh.** `refreshListener.accept` is only reached on `TICKED` (`:142-148`), so the GUI-refresh hook never fires either.
- **Claim blocked.** `claimAsync` requires `COMMITTED` (`PlayerHatchController.java:280-285`) → `MessageKey.HATCH_CLAIM_LOCKED` = `hatch.claim-locked` / "OmniPet: Egg payment is still being recovered; claim is locked." (`MessageKey.java:44-45`).
- **New start silently no-ops.** `HatchService.start:42` returns `ALREADY_INCUBATING` (`HatchResult.java:25`) → saga `:122-126` cancels the new escrow and only logs; the player still got `HATCH_START_QUEUED` on the main thread (`PlayerHatchController.java:177`). **`ALREADY_INCUBATING` is never rendered to the player anywhere** — grep of `MessageKey.java` shows no key carrying it.
- Only escape: operator `/omnipet admin hatch cancel <player> <incubation> <action>` (`OmniPetCommandTree.java:149-151`, `HatchAdminController.java:82-84`). The player-side GUI offers no cancel: renderer only wires start/claim/refresh/hub (`HatchMenuRenderer.java:41-62`).

**The literal 'already' strings.** Only one exists in the whole hatch path:
`MessageKey.HATCH_ACTION_IN_FLIGHT` = `hatch.action-in-flight` / **"OmniPet: A hatch action is already processing."** (`MessageKey.java:35`), emitted at `PlayerHatchController.java:170` when `mutations.add` fails. No `hatch.*` key contains the word "already" other than that one; no `already` string in any bundled `.yml`. So report B's "one 'hatch already' line" is either (a) that in-flight chat line from double-clicking slot 11 within the 10-tick window (`:184-185`), or (b) a paraphrase of the single frozen `GUI_HATCH_INCUBATION` "Incubation - incubating" head at slot 13 (`HatchMenuRenderer.java:50`, `:99-101`). The frozen-head reading is the one consistent with "does not update status while incubating".

Note the `mutations` guard itself does NOT leak: `finishStart` clears it after 10 ticks (`:184-185`, `:293-299`) and `release` clears it on inventory close (`HatchMenuListener.java:40-44`) and on quit/kick (`IncubationLifecycleListener.java:31-38` → `PlayerHatchController.release:148-152`).

---

## 3. GUI live-refresh verdict

**It does self-refresh — every 5 s, not per second — and only while the escrow is `COMMITTED`.**

- There is **no** repeating task bound to an open hatch inventory. Grep for `refreshListener` yields only `PaperIncubationCoordinator.java:29,80,148` and the single wiring `OmniPetPlugin.java:220` (`incubationCoordinator.setRefreshListener(hatchController::refresh)`).
- The only driver is the incubation ticker: `PaperIncubationCoordinator.start:50-54` with `TICK_PERIOD_TICKS = 100L` (`:20`) = **5 s**.
- On a successful tick it calls `refreshListener.accept(playerId)` (`:148`) → `PlayerHatchController.refresh:142-146`, which re-`open`s only if the top inventory holder is a `HatchInventoryHolder` — a genuine unsolicited redraw, no click needed.
- Slot 22 `CLOCK` refresh (`HatchMenuRenderer.java:55-58`) and slot 13 (non-READY → `Action.refresh()`, `:51-53`) are additional *manual* paths, not the only ones.
- Granularity: the lore prints `Durations.countdown(incubation.remainingActiveMillis())` (`HatchMenuRenderer.java:94-95`) off the durable snapshot, so the seconds field jumps in 5 s steps. First tick after a start credits 0 ms and returns early (`PaperIncubationCoordinator.java:134-136`), so the first visible change lands ~10 s in.
- Refresh is implemented as a full `openInventory` re-open (`PlayerHatchController.java:82`), which fires `InventoryCloseEvent` → `release` (`HatchMenuListener.java:43`) every 5 s. Cosmetic (cursor/sound flicker) and it drops the request token, but it does not break the redraw.

So: "GUI only redraws when you click slot 22" is **refuted as a design claim** — but is **exactly what a player observes** whenever escrow is not `COMMITTED` (§1b) or the incubation is not `INCUBATING`, because the refresh hook is downstream of `TICKED`.

---

## 4. What still needs fixing

| # | Defect | Status |
|---|---|---|
| A1 | Missing-`rarity` throw in the roll ⇒ egg never consumed, escrow `PREPARED`→auto-`CANCELLED`, "queued then nothing" | **Fixed** by 7015835 (`PetIncubationProfileReader.java:17-46`) |
| A2 | Egg tier drift also throws in the roller (`DeterministicHatchRollService.java:72-74`) | Mitigated by 7015835 (operator now told, `EggAdminController`); the roller still throws if a stale egg file survives |
| B1 | **Slot-strict `removeOne` vs nonce-loose `observe` ⇒ incubation `INCUBATING` + escrow pinned `PREPARED`: egg kept, countdown frozen, claim locked, new starts silently `ALREADY_INCUBATING`, self-heal loops forever every 5 s** | **STILL BROKEN — needs fix.** Recovery must re-locate the egg by nonce (as `observe` does) instead of requiring the original slot/held-hand, or the escrow must be refunded/cancelled after N failed recovery passes |
| B2 | Async start reports only *queued*; every downstream failure is log-only, so the player is never told the outcome (`PaperIncubationStartSaga.java:111,124,129,160`; `PlayerHatchController.java:177`) | **STILL BROKEN — needs fix.** No terminal player feedback path exists from the saga |
| B3 | `ALREADY_INCUBATING` has no `MessageKey`; a blocked re-start is indistinguishable from a queued one | **STILL BROKEN — cosmetic but it is why report B reads as "already"** |
| B4 | Player has no way out of a pinned escrow; only `/omnipet admin hatch cancel` | **STILL BROKEN — design gap** |
| B5 | Refresh re-opens the whole inventory every 5 s instead of `setItem` on slot 13 | Works; cosmetic flicker + spurious `InventoryCloseEvent`/`release` each cycle |
| B6 | Countdown granularity 5 s, first change ~10 s in | Works as designed; likely mismatches the reporter's expectation |

**Bottom line.** Reports A and B are **not the same bug**. A (as filed for a Studio pet with no rarity) is 7015835 and is fixed. B is *not* explained by 7015835 at all: the GUI does self-refresh, so the only way to observe a permanently static hatch screen is the still-live B1 escrow pin — which simultaneously reproduces "egg not consumed", meaning **a second, unfixed egg-not-consumed bug exists** and is the most probable source of report B (and possibly of report A, if the reporter's pet had rarity authored).

## Unresolved questions

1. Server logs would settle it in one line. Did the reporter's log carry `rarity must be a map` (⇒ A1, fixed) or `OmniPet left incubation escrow pending for <uuid>: NOT_MATCHING` (⇒ B1, live)? Both are `WARNING`.
2. Did the same player see a frozen countdown *and* a retained egg at the same time? If yes, B1 is confirmed empirically and A1 is excluded (A1 never persists an incubation).
3. Was the pet involved Studio-created without authored rarity bands? Needed to attribute report A.
4. Is the on-disk escrow yml for that incubation still at `PREPARED`? `FileEggEscrowJournal` never prunes (no delete/prune in the file), so the evidence should still be on the server.
