---
date: 2026-08-02
session: omnipet-hatch-gui-events-and-settlement-safety
status: verified-code-checkpoint
---

# Journal: 2026-08-02 - OmniPet Hatch GUI, Events, and Settlement Safety

## Context

This checkpoint extends the dependency-neutral hatch core into the Paper player flow. The authoritative boundary remains durable player state plus exact item escrow; a persisted hatch mutation is not proof that payment or item settlement completed.

## What Happened

- `/pet hatch` and `/pets hatch` now expose a 27-slot view with exact main-hand/off-hand starts, persisted-state refresh, active countdown, and `READY` claim. Countdown and claim remain locked until the matching escrow row is `COMMITTED`.
- Start preparation persists the escrow row and resolved incubation before exact one-item removal from the selected hand. Missing, disconnected, malformed, mismatched, or otherwise ambiguous observations remain durable for conservative recovery instead of being destructively cancelled. Recovery is bounded and deduplicated per player/transaction.
- Online timing now begins when `COMMITTED` is observed. A monotonic per-player/incubation baseline advances only after a successful persisted tick, so failed persistence retains elapsed time for retry and pre-commit samples do not count.
- `RepositoryHatchService` now emits structured `HatchEvent` notifications at `DeliveryStage.STATE_PERSISTED` only after a changed state is saved. Unchanged results emit nothing; listener registration is idempotent; observer failures are isolated; queued re-entrant delivery preserves order; and terminal-to-new-incubation transitions do not mix identities.
- The proposed reducer/instant-hatch consumable path was rejected and removed. Consuming the item after persisted state would allow crash-time free effects, while copying action tokens into item state would not provide durable redemption or ownership settlement. These consumables remain deferred until a durable redemption/escrow contract exists.
- Java 21 Gradle verification reports 83 suites and 290 tests (40 core suites/164 tests; 43 Paper suites/126 tests), with zero failures, errors, or skips. The release JAR is 1,010,248 bytes with SHA-256 `71F381BF1BFBA125CDE35D8883734F8CC09BAAF559680D0DB2AB52588F3325B7`.

## Reflection

The implementation now makes the important distinction visible in both code and UI: state persistence, inventory observation, and escrow settlement are separate facts. That separation keeps retries and restart recovery conservative, and prevents low-level state events from becoming an accidental payment API.

## Decisions Made

| Decision | Rationale | Impact |
| --- | --- | --- |
| Gate countdown and claim on escrow `COMMITTED` | `STARTED` state or `STATE_PERSISTED` event cannot prove item removal/payment | No premature time advancement or claim |
| Preserve ambiguous escrow for recovery | Filesystem state and live Bukkit inventory are not atomic | Recovery can require observation or operator review without guessing |
| Advance monotonic baseline after successful persistence | Failed ticks must accumulate rather than lose time or double-count | Online timing is retry-safe |
| Keep persisted-state events in-repo and observer-isolated | Consumers need a stable low-level seam without settlement claims | Ordered, non-failing notifications; no public addon/event-bus contract |
| Defer reducer/instant items | Token idempotency alone cannot make item redemption durable | No native consumable behavior is claimed yet |

## Next Steps

- Add crash-injection proof at every escrow boundary, including process-kill/restart rollback bounds.
- Complete live Paper/server lifecycle and inventory smoke certification for join, quit, recovery, timing, GUI, and claim.
- Design and verify durable item redemption/escrow before reintroducing reducer or instant-hatch consumables.
- AgentWiki publishing was skipped because the AgentWiki CLI/MCP capability is unavailable.

## Unresolved Questions

- Confirm whether `headbase` refers to HeadDatabase or another head catalog before pinning an integration API.
- Decide whether an optional temporary world `TextDisplay` countdown preview is needed; GUI item text remains the default.
