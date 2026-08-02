# OmniPet

OmniPet is a Paper plugin rewrite. This checkout contains the verified Gradle-only foundation, Admin Pet Studio, the player-storage/economy slice, the dependency-neutral incubation core with the Paper paid-start/recovery coordinator and escrow-gated hatch GUI, live pet rendering behind one bounded coordinator, reservation-based skills, and durable cultivation and release transactions. Riding, entity click interaction, and live-server certification remain phased work.

## Shipped foundation and Studio

- Gradle Wrapper 9.1.0 is the only supported build authority. The clean verification command is `gradlew.bat clean build` on Windows or `./gradlew clean build` on Linux/macOS.
- `omnipet-core` contains dependency-neutral domain, YAML codecs, migration readers, atomic storage, path checks, and repository/registry seams. `omnipet-paper` contains the Paper bootstrap and produces the installable distribution JAR.
- Java 21 is the source and release baseline. Resources and Java compilation use UTF-8.
- The descriptor is branded `OmniPet`, authored by `SalyVn`, and loads `io.github.salyvn.omnipet.paper.OmniPetPlugin`.
- Paper registers `/pet` with `/pets` as its alias. Both names expose the player vault, `/pet hatch [main|off|claim|refresh]`, and the live `/pet admin browse` Studio branch.
- `omnipet.general` defaults to `true`; `omnipet.*` defaults to `op`; declared `omnipet.admin.*` nodes default to `false`.
- Player envelopes use schema version 4 and pet definitions use schema version 2. The typed singular incubation state snapshots the resolved pet definition/icon, rarity, quality, seed/algorithm, realized stats, active-time budget, status, and bounded idempotency tokens. Schema 1-3 migration preserves raw `currentEgg` and arbitrary legacy `incubation` nodes; unresolved legacy incubation data blocks a new core start instead of being discarded.
- Atomic writes create `.bak` snapshots when replacing an existing file. Invalid player files are quarantined; a quarantined profile fails closed until an operator performs explicit recovery.
- Legacy egg definitions are validated and written to `migration/legacy-eggs-v1.yml` with a semantic SHA-256 journal. The source `eggs.yml` is never overwritten by that journal.
- Canonical egg definitions use schema 1 and one file per ID under `plugins/OmniPet/eggs/*.yml`. The core repository enforces matching filename/`eggId`, safe case-unique IDs, tier, bounded compact/compound/ISO duration, weighted candidates, a 64 KiB file limit, and a 10,000-entry catalog bound. Root `eggs.yml` remains migration input only.
- `HatchService` provides deterministic `splitmix64-v1` start/tick/reduce/set/complete/cancel/claim transitions. Claim is atomic and vault-capacity-safe; a full vault leaves the incubation `READY` for a later claim. `RepositoryHatchService` also exposes the in-repo `HatchEvent`/`HatchEventListener` seam after successful persisted changes; events carry `DeliveryStage.STATE_PERSISTED`, unchanged mutations emit nothing, and observer failures cannot fail the mutation. This low-level state notification is not proof that egg escrow/payment committed.
- The core item-escrow saga uses the incubation UUID as its transaction UUID and persists item hand, slot, nonce, fingerprint, and expected amount. Atomic compare-transitions cover `PREPARED -> ITEM_REMOVED -> COMMITTED`, cancellation/refund/failure paths, bounded scans, and fail-closed operator review. Paper loads canonical definitions from `plugins/OmniPet/eggs/*.yml`, binds the durable journal at `plugins/OmniPet/data/egg-escrow/*.yml`, and caches pet-to-egg references for Studio deletion checks.
- Paper egg items use `omnipet:egg`, `omnipet:item_nonce`, and `omnipet:item_schema`, with dual-read support for legacy `passivepet:egg`. The durable snapshot normalizes the serialized payload to amount one and rejects payloads over 8 KiB. Inventory mutation requires an online player on the Paper main thread; malformed identities, duplicate nonces, mismatched fingerprints/materials, and unexpected split amounts fail closed as ambiguous.
- The Paper incubation coordinator persists `PREPARED` escrow and the resolved incubation before removing one exact egg from the selected main/off-hand stack, then acknowledges `ITEM_REMOVED` and `COMMITTED`. A shared per-player queue serializes journal/state work. Online time starts from the monotonic commit observation. The persist cadence is 100 ticks and is the documented maximum active-time rollback: a tick that cannot persist pauses accrual rather than accumulating unpersisted time in memory.
- Start failures, pending online ticks, and player joins can request conservative recovery. Recovery scans actionable non-terminal escrow stages first with a bounded per-player limit; if the bound is reached, OmniPet logs that older pending records need operator review instead of silently claiming a complete scan. Disconnects leave pending escrow durable; cancellation retries use a stable transaction-derived action token.
- `/pet hatch` renders a 27-slot durable view: empty/terminal states offer exact main-hand and off-hand starts, active incubation shows its snapshot/countdown, `READY` exposes claim, and slot 22 refreshes current persisted state. Countdown and claim stay locked until escrow is `COMMITTED`; a persisted `STARTED` state or core event alone is not payment proof.
- `/pet admin browse` and `/pets admin browse` open the D/C/B/A/S Studio tier browser for `omnipet.admin.managepet` staff. The Studio supports paginated definition lists, a reflection-safe MythicLib stat picker with manual fallback, create/edit drafts, typed rarity/progression/skill/release fields, archive mode, safe inventory events, chat input expiry/cancel, optimistic conflict checks, and atomic Save/reload generation swaps.
- `/pet [page]` and `/pets [page]` open the UUID/revision-safe player vault. One plugin-owned `PerPlayerTaskQueue` is injected into the vault and slot-purchase controllers: accepted work runs FIFO per player across both controllers, while different players may run concurrently. Only pending reads with the matching namespaced key (`vault:view` or `slot:view`) coalesce; reconciliation, mutations, and purchases never coalesce. Repository work stays async; Bukkit inventory, permission, and provider work stays on the main thread.
- `/pet slot` opens the next-slot purchase flow. Vault and PlayerPoints prices are separate choices with balance display and confirmation; no currency is auto-selected. Unknown provider outcomes remain reconciliation-gated.
- `/pet admin transactions [limit] [cursor]` and `/pet admin reconcile <transaction-uuid> <charge|no-charge|refund|sync>` expose bounded, audited recovery without blindly replaying economy calls. The optional cursor is an opaque continuation token printed by OmniPet and must be reused verbatim. Each journal file is capped at 16 KiB; a page reports at most 20 unreadable issues plus omission/truncation summaries. Failed LuckPerms propagation remains durably `ENTITLEMENT_SYNC_PENDING` until an idempotent `sync` retry succeeds.
- Legacy purchase-journal schema 1 rows in `COMPLETED`, `ENTITLEMENT_PERSISTED`, or `ENTITLEMENT_SYNC_PENDING` are conservatively reopened as `ENTITLEMENT_SYNC_PENDING`. OmniPet does not replay the economy operation; successful `sync` verification rewrites schema 2 atomically and retains the schema 1 file as `.bak`.
- Provider lifecycle changes invalidate only the affected Vault/service-owner, PlayerPoints, or LuckPerms adapter. Healthy unrelated providers remain available while one coalesced refresh republishes valid adapters on the next tick.
- Provider scheduling, registration, and shutdown share one lifecycle lock. During shutdown, controllers stop intake and close relevant UIs, provider bridges close/cancel, then the shared player queue rejects new work, drops pending coalesced reads, and drains accepted mutations for up to 10 seconds. A dispatch rejection cannot erase another accepted task.
- `config.yml` owns separate vault/active limits, unlock prices, eligibility nodes, and explicit OmniPet/LuckPerms/hybrid precedence. The LuckPerms permission template is expanded and validated through the configured active-slot maximum before activation. Exact legacy `globalMaxSlots`/`slotPermission` files are migrated atomically with `config.yml.bak`. It also owns runtime coordinator bounds, progression defaults with a compiled and sampled EXP formula, and standalone cultivation item identities; a storage-only file still loads with documented defaults.

## Shipped runtime, skills, and progression

- One bounded main-thread coordinator updates every active pet from immutable storage snapshots, visiting owners round-robin within configured per-tick limits. There is no per-pet task. Deterministic 100-pet operation counts are recorded in the plan's runtime budget evidence; live TPS measurement is still a release gate.
- The built-in head renderer moves by interpolation and reserves hard teleport for world change, invalid entity, or the distance safety threshold. Partial spawn failure rolls back created entities and invalid entities retire the handle for activation respawn. A claimed pet snapshots its head source, so it stays renderable if the definition is later archived.
- The reflective ModelEngine adapter falls back to the head renderer on reflection failure, `LinkageError`, or spawn failure, and quarantines itself for the current provider epoch. Riding is advertised as unsupported by the head renderer and no mount service exists yet.
- Active skills reserve stamina and cooldown only after preconditions pass and commit only after the provider reports success; failure rolls the reservation back. Reservations are durable and bounded, so a restart cannot recast or double-charge. Cooldown persistence is per binding: `persistCooldown` survives restart, cosmetic cooldowns stay in memory. `/pet skill <pet-uuid> <binding-id>` casts and `/pet admin skill` inspects or rolls back pending reservations.
- MythicMobs casting and MythicLib owner buffs are reflective and optional; an absent, disabled, or wrong-ABI plugin disables only that capability.
- Right-clicking a vault pet opens management: favorite, lock, reorder, EXP candy, breakthrough, and release. Every rendered inventory binds viewer, owner, pet UUID, session, generation, and expected revision; drags touching the top inventory are cancelled and a late async result cannot replace a newer view.
- Cultivation and reducer/instant-hatch items redeem through durable journals: the exact item identity is recorded, the item is removed on the main thread, and the effect applies once per transaction. Restart after removal commits without reapplying; a rejected mutation refunds the exact item; an ambiguous boundary requires operator review.
- Release freezes a reward preview, then removes the pet and appends the internal outbox entry in one player-state write. Internal rewards deliver through an idempotent durable mailbox; ambiguous external Vault/PlayerPoints outcomes persist as `UNKNOWN_COMMIT` and wait for `/pet admin release` reconciliation instead of blind retry.
- Every accepted Studio save, archive, and hard delete writes one durable audit record, rolled back with the definition if activation fails.

## Deferred roadmap

The following remain phased work: live provider/server certification, crash-injection and process-kill certification, riding, entity click interaction and passive event triggers, a combined player hub, MMOItems cultivation identities, a live MythicMobs skill picker in the Studio, an optional head-catalog provider, admin target mode for another player's pets, and a separately versioned addon/live API. See [the roadmap](docs/roadmap.md) for ownership and release gates.

## Compatibility

Compatibility jobs are compile probes, not live-server certification:

| Paper API probe | Java toolchain | Position |
| --- | ---: | --- |
| `1.21-R0.1-SNAPSHOT`, `1.21.11-R0.1-SNAPSHOT` | 21 | Primary baseline probes |
| `26.1.1.build.29-alpha` | 25 | Preview/alpha probe |
| `26.1.2.build.74-stable`, `26.2.build.87-stable` | 25 | Experimental/forward probes |

Use the exact build matrix in [compatibility](docs/compatibility.md). Passing a probe does not certify Paper runtime behavior, vendor plugins, or future 26.x versions.

## Build and artifact

From this directory:

```text
gradlew.bat clean build --no-daemon --console=plain
```

The single release artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar` (copied from `omnipet-paper/build/libs/`). The 2026-08-02 clean JDK 21 Gradle hatch checkpoint passed all 18 tasks and 132 suites/460 tests: core 62 suites/237 tests and Paper 70 suites/223 tests, with zero failures, errors, or skips. It produced a 1,589,438-byte JAR with SHA-256 `199E919F7123074A67EE5F4BF913AFF9CBCE86C28B9D281D14A07810D4E81584`: 976 entries, 888 classes, one `paper-plugin.yml`, and zero Maven entries. This checkpoint did not rerun compatibility probes, crash-injection tests, or live-server certification. Maven commands, `pom.xml`, Maven wrappers, and the old `passivepet2` artifact name are not part of the supported workflow.

## Migration warning

Before any rebrand or data-folder move, stop the server and make a complete backup. Preserve player, pet, egg, component, and user-defined IDs. Do not run the old plugin and OmniPet against the same data at the same time. Read [Migration from PassivePet](docs/migration.md) for the legacy warnings, journal behavior, `.bak` handling, quarantine rules, and rollback procedure.

## Documentation

- [Getting started](docs/getting-started.md)
- [Commands and permissions](docs/commands-and-permissions.md)
- [Compatibility](docs/compatibility.md)
- [Configuration and schema](docs/configuration.md)
- [Developer guide](docs/developer-guide.md)
- [Migration](docs/migration.md)
- [Roadmap](docs/roadmap.md)

Configuration and slot-provider sections identify their current authoritative contracts. The schema 1 egg example is consumed by the shipped Paper hatch orchestration after restart; renderer, trigger, reducer/instant-item, vendor, and other future examples remain non-authoritative until their owning phases ship.

The public [GitHub Pages manual](https://salyys1.github.io/OmniPet/) is deployed from `main`. Changes in this working branch are not public until merged and deployed successfully. The repository wiki is enabled but has no initial page yet, so repository docs remain authoritative.
