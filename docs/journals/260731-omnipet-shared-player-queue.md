---
date: 2026-07-31
session: omnipet-shared-player-queue
status: verified-checkpoint
---

# Journal: 2026-07-31 - OmniPet Shared Player Queue

## Context

Phase 4 needed one ordering boundary for player storage, reconciliation, and slot purchases. Separate controller-owned queues could serialize each feature internally while still racing against the other feature for the same player. The Gradle-only greenfield modules remain authoritative; the legacy tree remains migration and behavior reference only.

## What Changed

- `OmniPetPlugin` now owns one `PerPlayerTaskQueue` and injects it into both the vault and slot-purchase controllers.
- Accepted tasks run FIFO per player across both controllers. Different players can still run concurrently.
- Only pending reads with the exact namespaced keys `vault:view` or `slot:view` coalesce. Storage reconciliation, pet mutations, and purchases use non-coalesced submission.
- Shared request tracking moved into the task package, while request generations and inventory context continue to reject stale UI completions.
- Disable now stops controller intake and closes relevant UIs, closes/cancels provider bridges, then shuts down the queue. Pending coalesced reads are dropped; accepted mutations drain for up to 10 seconds.
- Provider scheduling, registration, and shutdown now share a lifecycle lock so shutdown observes and cancels every accepted future.

## Race Failures Found and Fixed

- **Dispatch rejection/admission race:** a first executor dispatch could fail while another thread submitted work for the same player. Clearing the queue on rejection could erase the concurrently accepted task. Admission now waits through the dispatch boundary, then retries against the current per-player queue; the second task remains accepted and executes.
- **Provider scheduling/shutdown race:** shutdown could set its flag and scan active futures while a caller was between the availability check and future registration. That future escaped cancellation. Scheduling and active-set registration now happen under the same lifecycle lock used by shutdown.
- **Shutdown dependency race:** queue shutdown before provider closure could strand accepted mutations waiting for main-thread provider work. Provider bridges now close before queue shutdown and drain.
- **Duration overflow:** `Duration.toNanos()` could overflow for very large drain timeouts. Conversion now saturates at `Long.MAX_VALUE`.

## Verification Evidence

- Independent review: PASS, no findings.
- Java 21 `gradlew.bat clean build --no-daemon --console=plain`: `BUILD SUCCESSFUL` in 39 seconds.
- Tests: 68 suites/249 tests; core 39/157 and Paper 29/92; zero failures, errors, or skips.
- Release JAR: 934,381 bytes; SHA-256 `2573ACD0FAC3BA19CBAC6397D21DC486DCE8609445821CED712BACBCBB00C9C1`.
- Artifact inspection: 615 entries, 542 classes, one `paper-plugin.yml`, zero forbidden bundled entries. Maven build-path scan: zero entries.
- Compatibility compile probes and live Paper/provider probes were not rerun for this checkpoint.
- AgentWiki publishing was skipped: no AgentWiki CLI command or MCP/tool capability was available, and no public publishing authority was granted.

## Decisions and Trade-offs

| Decision | Rationale | Trade-off |
| --- | --- | --- |
| One plugin-owned queue | Cross-feature mutations for one player need one FIFO authority | Controllers depend on plugin-level lifecycle ownership |
| Coalesce namespaced reads only | Superseded views are disposable; mutations and purchases are not | More accepted mutation work may drain during shutdown |
| Close providers before queue shutdown | Accepted mutations may still need provider completion | Disable can wait up to the bounded 10-second drain |
| Preserve concurrency across players | Per-player ordering does not require global serialization | Queue implementation carries per-player dispatch state |
| Saturate huge durations | Defensive timeout handling must not fail by arithmetic overflow | Extreme values behave as effectively unbounded waits |

## Next Steps

- Run live Paper boot/disable, inventory, Vault, PlayerPoints, and LuckPerms certification.
- Route every future pet-add and Paper incubation mutation through the shared per-player boundary.
- Continue Phase 3 Paper item/PDC, online checkpoint, recovery, command/GUI, and claim orchestration work.
- Keep compatibility probes as separate compile evidence; do not treat them as runtime certification.

## Unresolved Questions

- None for the shared-queue checkpoint. Existing head-provider and optional world countdown-preview decisions remain open in the parent plan.
