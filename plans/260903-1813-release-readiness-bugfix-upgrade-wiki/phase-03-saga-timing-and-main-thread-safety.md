---
phase: 3
title: "Saga timing and main-thread safety"
status: pending
priority: P1
effort: "3d"
dependencies: [2]
---

# Phase 3: Saga timing and main-thread safety

## Context Links

- Findings source: `plans/reports/from-scout-sagas-to-planner-incubation-economy-release-defect-review-report.md`

## Overview

Fix the three saga P1s — ready placed eggs that never hatch, egg duplication/griefing through event-priority persistence, and the slot-purchase tick freeze — plus the release-delivery and action-item P2s. Depends on Phase 2's lock-registry deadline.

## Key Insights

- `PlacedEggView.onReady` fires only at the exact tick transition; instant-hatch `spend()`, offline owner, or full vault leaves an egg READY forever with no retry and no menu CLAIM (`PlacedEggView.java:217-220`, `PlacedEggCoordinator.java:172`, `OmniPetPlugin.java:475,480-487`).
- `PlacedEggListener` persists the record at NORMAL priority before WorldGuard-class plugins cancel at HIGH/HIGHEST, and `onBreak` has no `isEggBlock`/owner gate → duplication + griefing (`PlacedEggListener.java:87-134`).
- Slot purchase withdraws inside the player file lock while blocking up to 10s on `callSyncMethod`; main-thread `redeem()` takes the same lock → server-wide tick freeze (`SlotUnlockService.java:94,255`, `PaperProviderCallExecutor.java:47`, `IncubationActionItemController.java:71-89`).
- Test comment `PlacedEggSupportReductionTest.java:112` says "a ready egg is claimed by breaking it". **Decided (validation 2026-09-03):** all three paths ship — retry auto-hatch for online owners, a CLAIM action in the placed-egg menu, and breaking a READY egg grants the pet rather than returning the raw egg. Update that test's comment and assertions to the decided semantics.

## Requirements

- Functional: all fixes below preserve on-disk formats and public core APIs (schema versions unchanged). Placed-egg claim semantics per the binding decision in `plan.md`: retry + CLAIM + break-grants-pet.
- Non-functional: no repository locks held across provider calls or main-thread hops; all handlers idempotent.

## Architecture

- **Ready-egg recovery:** `PlacedEggCoordinator.pass()` also invokes `onReady` for every `record.ready()` with online owner, guarded by an in-flight key set cleared in the grant callback; `spend()` calls the same hook when the reduction makes it ready. Grant failure (vault full, definition missing) messages the owner with reason and retries on next pass/join. The placed-egg menu gains a CLAIM action alongside HUB/REFRESH/REDEEM, and breaking a READY egg grants the pet instead of returning the raw egg (elapsed time is never discarded).
- **Placement/break safety:** all nine `PlacedEggListener` handlers currently run at NORMAL with `ignoreCancelled = true` (onInteract:74, onPlace:87, onBreak:117, onTrample:153, onPhysics:168, onEntityExplode:175, onBlockExplode:180, onPistonExtend:185, onPistonRetract:190), and `event.setDropItems(false)` is line 122 inside `onBreak` — there is no LOWEST handler today. The split to build: drop suppression stays in the NORMAL `onBreak`; record persistence on place and reclaim/grant on break move to new MONITOR handlers. Because `ignoreCancelled = true` at MONITOR already means a protection cancel wins, no extra `isCancelled()` re-check is needed on either path — rely on the priority, not a manual guard. Creative mode handled explicitly (`player.getGameMode()`). Break requires `PlacedEggBlocks.isEggBlock(block)` + owner-or-admin; non-owners get `PLACED_EGG_NOT_OWNER`.
- **Provider calls outside locks:** `SlotUnlockService` persists `EXTERNAL_PENDING`, releases the player lock, calls the provider, re-locks to record the outcome (recovery matrix already covers `EXTERNAL_PENDING` crash windows — verified: `SlotPurchaseRecovery.java:30,181`, `SlotPurchaseReconciliationService.java:15`).

  **The re-lock must not write the pre-call snapshot.** Today `case PROVEN_SUCCESS -> SlotPurchaseSagaSupport.grant(current, observed)` (`SlotUnlockService.java:262`) grants using the `PlayerState current` captured before `support.withdraw(pending)` (`:255`). That is safe only while the lock is held for the whole span; once it is released across the provider call, `current` is arbitrarily stale and the outcome write becomes either a lost write or a `StaleRevisionException` — *after* the money has left the player, which is the worst possible moment. So after re-locking: re-read player state and re-read the journal transaction, re-check `hasSlotEntitlement` and the next-slot preconditions against the fresh state, and route any divergence to `UNKNOWN_REQUIRES_RECONCILIATION` (already modelled) instead of writing the snapshot. Test: mutate player state during the simulated provider call and assert reconciliation, not a lost grant.

  `PaperProviderCallExecutor` cancels the sync future on timeout; uncancellable → UNKNOWN. Action-item redeem moves to the per-player queue with a main-thread hop only for item consume (mirror `PaperIncubationStartSaga`).
- **Release delivery:** join-time async recovery `delivery.pending(playerId, N)` → `recoverInternal` sequential (shape of `PaperCultivationRecoveryController.onJoin`); mailbox terminal entries archived and a per-player index added so `findByPlayer` is O(that player's entries) rather than a directory scan — archival alone only shrinks the scan; `list` truncates with flag instead of throwing; INVENTORY_CLAIMING self-heals via gateway PDC evidence; reward stacks capped at inventory capacity.

  Verified while planning (`BukkitReleaseInventoryGateway.java:20-48,60-90`): the gateway already stamps each reward stack's PDC with the transaction id and returns `ALREADY_DELIVERED` when the tagged amounts already match, so internal delivery *is* idempotent per transaction — but only while the player still holds the items. Once they are consumed, moved to a chest, or dropped, the scan finds nothing and a replay would duplicate. So the PDC evidence is enough to justify re-running `claim()` for INVENTORY_CLAIMING instead of forcing UNKNOWN, and Phase 2's persisted `INTERNAL_ATTEMPTING` marker is still required — inventory evidence cannot replace it.

## Related Code Files

- Modify: `omnipet-paper/.../incubation/placed/PlacedEggView.java`, `PlacedEggCoordinator.java`, `PlacedEggListener.java`, `PlacedEggSupportController.java`, `PlacedEggHolograms.java` (chunk-loaded guard in `refresh`, respawn on `ChunkLoadEvent`)
- Modify: `omnipet-paper/.../OmniPetPlugin.java` (`hatchPlacedEgg` failure reporting + `invalidateDefinitions()` after reload)
- Modify: `omnipet-core/.../economy/SlotUnlockService.java` (EXTERNAL_PENDING release-lock pattern), `omnipet-paper/.../economy/PaperProviderCallExecutor.java` (`future.cancel(false)`)
- Modify: `omnipet-paper/.../incubation/action/IncubationActionItemController.java`, `IncubationItemActionCoordinator.java` (off-main redeem)
- Modify: `omnipet-paper/.../release/FileReleaseRewardMailbox.java` (archival + truncation + corrupt-file tolerance), `PaperReleaseInternalRewardPort.java` (INVENTORY_CLAIMING self-heal), `BukkitReleaseInventoryGateway.java` (stack cap), `ReleaseAdminController.java` (online-default list, actor in reconcile evidence), `management/OmniPetManagementServices.java` (join recovery hook)
- Modify (P3 batch): `EggAdminController.java` (propagate grant failure detail), `PaperIncubationRecoveryExecutor.java` (refund message + re-snapshot revision), `PaperEggItemCodec.java` (per-item nonce on legacy stacks), `PaperPlayerEggInventory.java` (cursor slot), `PlayerSlotPurchaseController.java` (balance read on main), `PaperReleaseExternalRewardPort.java` (evict attempts), `PlayerHatchController.java` (clear mutations from saga callback), `PaperIncubationItemActionCodec.java` (formatting; cache the PDC decode instead of re-deserializing per `supports()` call), merge `PaperIncubationItemActionInventory` + `PaperPlayerEggInventory` scan logic into one helper

## Implementation Steps

1. Ready-egg retry + spend hook + in-flight guard; tests: instant-hatch → pet granted; owner offline → granted on next pass after join; vault full → message + retry succeeds after slot freed. Also call `invalidateDefinitions()` from `reloadRuntime` (`OmniPetPlugin.java:425-459`, `PlacedEggCoordinator.java:268-270`) — nothing calls it today, so placement requirements stay stale after a catalog reload; test that a reloaded requirement takes effect.
1b. Hologram/chunk hygiene: `PlacedEggView.refresh` skips records whose chunk is unloaded (today `start()` force-loads a chunk per placed egg at enable, stalling boot proportional to egg count) and holograms (re)spawn on `ChunkLoadEvent`; test both.
2. Placed-egg listener rework (persistence and reclaim move to MONITOR handlers, creative handled, isEggBlock + owner gate); tests simulate a HIGHEST canceller: no record on cancelled place, no reclaim on cancelled break, non-owner break refused. Leave the trample/physics/explosion/piston handlers at their current priorities untouched — they are the shipped fix for eggs being destroyed by entities.
3. READY-egg break grants the pet; CLAIM action added to the placed-egg menu; `PlacedEggSupportReductionTest` comment + assertions updated to the decided semantics.
4. SlotUnlockService EXTERNAL_PENDING pattern + fresh-state re-read on re-lock + executor future-cancel; tests: recovery matrix passes for a crash between persist and provider return; player state mutated during the provider call resolves to reconciliation rather than a lost grant; no lock held during provider call (assert via lock registry).
5. Action-item redeem off main; main-thread assert flipped to worker assert where moved.
6. Join-time release recovery + mailbox archival + INVENTORY_CLAIMING self-heal + stack cap; tests for each. Same step: `ReleaseAdminController.list` defaults to online players with an `--all` opt-in and a bounded scan (today it does a locked read per player across up to 10k ids), and `reconcile` records the acting sender's name as evidence instead of the constant "operator reconciliation" — parity with `SlotTransactionAdminController:138`, and it is the audit trail for a money-adjacent command.
7. P3 batch with focused tests (legacy stack nonce split, cursor slot, refund message).
8. Full paper + core test run.

## Todo

- [ ] Ready-egg retry pass + spend hook + failure messaging; `invalidateDefinitions()` on reload
- [ ] Chunk-loaded guard in hologram refresh + ChunkLoadEvent respawn
- [ ] Persistence + reclaim moved to MONITOR handlers; creative handled on place
- [ ] isEggBlock + owner/admin gate + MONITOR reclaim on break
- [ ] READY break grants pet + CLAIM menu action
- [ ] Provider calls outside player lock; fresh state re-read on re-lock; future.cancel on timeout
- [ ] Action-item redeem off main thread
- [ ] Join-time release outbox + mailbox recovery; mailbox archival + per-player index
- [ ] InternalRewardPort INVENTORY_CLAIMING self-heal (PDC evidence) + stack cap
- [ ] Admin `release list` online-default + bounded scan; reconcile records the actor
- [ ] Saga P3 batch
- [ ] Green: full build

## Success Criteria

- [ ] Placed egg made READY by any path hatches for an online owner within one coordinator pass, or messages the owner why not
- [ ] Protection-plugin cancel leaves world + records consistent (no dup, no orphan) — proven by listener-order tests
- [ ] Enable does not force-load a chunk per placed egg (hologram refresh skips unloaded chunks; `ChunkLoadEvent` respawns)
- [ ] No repository lock is held across `callSyncMethod` or provider calls (assertion + test)
- [ ] A provider call whose player state changed underneath resolves to reconciliation, never a lost grant
- [ ] Player with full inventory at release receives rewards on next join without operator action

## Risk Assessment

- Event-priority rework can regress the shipped trample/physics protections (CHANGELOG "Walking over a placed egg…") — keep those handlers untouched at their priorities; add a regression test per protection.
- Moving withdraws outside the lock widens the UNKNOWN window → the recovery matrix + reconcile command already own that path; test crash-window recovery explicitly.
- Legacy nonce split mutates player inventories on load — gate behind a one-time migration with journal evidence, test with amount>1 fixtures.

## Security

- Owner gate on break closes a griefing/theft vector on a durable owned asset. MONITOR persistence closes a region-protection bypass. No new permissions beyond `omnipet.admin.*` reuse for admin break.
