# OmniPet

OmniPet is a Paper plugin rewrite. This checkout contains the verified Gradle-only foundation, Admin Pet Studio, the player-storage/economy slice, the dependency-neutral Phase 3 incubation core, and the current Paper egg catalog/PDC/inventory/escrow bridge checkpoint. The live start coordinator, online scheduler, hatch commands/GUI, recovery executor, and live pet entities remain phased work.

## Shipped foundation and Studio

- Gradle Wrapper 9.1.0 is the only supported build authority. The clean verification command is `gradlew.bat clean build` on Windows or `./gradlew clean build` on Linux/macOS.
- `omnipet-core` contains dependency-neutral domain, YAML codecs, migration readers, atomic storage, path checks, and repository/registry seams. `omnipet-paper` contains the Paper bootstrap and produces the installable distribution JAR.
- Java 21 is the source and release baseline. Resources and Java compilation use UTF-8.
- The descriptor is branded `OmniPet`, authored by `SalyVn`, and loads `io.github.salyvn.omnipet.paper.OmniPetPlugin`.
- Paper registers `/pet` with `/pets` as its alias. Both names expose the phased player entry point and the live `/pet admin browse` Studio branch.
- `omnipet.general` defaults to `true`; `omnipet.*` defaults to `op`; declared `omnipet.admin.*` nodes default to `false`.
- Player envelopes use schema version 4 and pet definitions use schema version 2. The typed singular incubation state snapshots the resolved pet definition/icon, rarity, quality, seed/algorithm, realized stats, active-time budget, status, and bounded idempotency tokens. Schema 1-3 migration preserves raw `currentEgg` and arbitrary legacy `incubation` nodes; unresolved legacy incubation data blocks a new core start instead of being discarded.
- Atomic writes create `.bak` snapshots when replacing an existing file. Invalid player files are quarantined; a quarantined profile fails closed until an operator performs explicit recovery.
- Legacy egg definitions are validated and written to `migration/legacy-eggs-v1.yml` with a semantic SHA-256 journal. The source `eggs.yml` is never overwritten by that journal.
- Canonical egg definitions use schema 1 and one file per ID under `plugins/OmniPet/eggs/*.yml`. The core repository enforces matching filename/`eggId`, safe case-unique IDs, tier, bounded compact/compound/ISO duration, weighted candidates, a 64 KiB file limit, and a 10,000-entry catalog bound. Root `eggs.yml` remains migration input only.
- `HatchService` provides deterministic `splitmix64-v1` start/tick/reduce/set/complete/cancel/claim transitions. Claim is atomic and vault-capacity-safe; a full vault leaves the incubation `READY` for a later claim.
- The core item-escrow saga uses the incubation UUID as its transaction UUID and persists item hand, slot, nonce, fingerprint, and expected amount. Atomic compare-transitions cover `PREPARED -> ITEM_REMOVED -> COMMITTED`, cancellation/refund/failure paths, bounded scans, and fail-closed operator review. Paper loads canonical definitions from `plugins/OmniPet/eggs/*.yml`, binds the durable journal at `plugins/OmniPet/data/egg-escrow/*.yml`, and caches pet-to-egg references for Studio deletion checks.
- Paper egg items use `omnipet:egg`, `omnipet:item_nonce`, and `omnipet:item_schema`, with dual-read support for legacy `passivepet:egg`. The durable snapshot normalizes the serialized payload to amount one and rejects payloads over 8 KiB. Inventory mutation requires an online player on the Paper main thread; malformed identities, duplicate nonces, mismatched fingerprints/materials, and unexpected split amounts fail closed as ambiguous. These are verified bridge seams, not a live hatch start or recovery executor.
- `/pet admin browse` and `/pets admin browse` open the D/C/B/A/S Studio tier browser for `omnipet.admin.managepet` staff. The Studio supports paginated definition lists, a reflection-safe MythicLib stat picker with manual fallback, create/edit drafts, typed rarity/progression/skill/release fields, archive mode, safe inventory events, chat input expiry/cancel, optimistic conflict checks, and atomic Save/reload generation swaps.
- `/pet [page]` and `/pets [page]` open the UUID/revision-safe player vault. One plugin-owned `PerPlayerTaskQueue` is injected into the vault and slot-purchase controllers: accepted work runs FIFO per player across both controllers, while different players may run concurrently. Only pending reads with the matching namespaced key (`vault:view` or `slot:view`) coalesce; reconciliation, mutations, and purchases never coalesce. Repository work stays async; Bukkit inventory, permission, and provider work stays on the main thread.
- `/pet slot` opens the next-slot purchase flow. Vault and PlayerPoints prices are separate choices with balance display and confirmation; no currency is auto-selected. Unknown provider outcomes remain reconciliation-gated.
- `/pet admin transactions [limit] [cursor]` and `/pet admin reconcile <transaction-uuid> <charge|no-charge|refund|sync>` expose bounded, audited recovery without blindly replaying economy calls. The optional cursor is an opaque continuation token printed by OmniPet and must be reused verbatim. Each journal file is capped at 16 KiB; a page reports at most 20 unreadable issues plus omission/truncation summaries. Failed LuckPerms propagation remains durably `ENTITLEMENT_SYNC_PENDING` until an idempotent `sync` retry succeeds.
- Legacy purchase-journal schema 1 rows in `COMPLETED`, `ENTITLEMENT_PERSISTED`, or `ENTITLEMENT_SYNC_PENDING` are conservatively reopened as `ENTITLEMENT_SYNC_PENDING`. OmniPet does not replay the economy operation; successful `sync` verification rewrites schema 2 atomically and retains the schema 1 file as `.bak`.
- Provider lifecycle changes invalidate only the affected Vault/service-owner, PlayerPoints, or LuckPerms adapter. Healthy unrelated providers remain available while one coalesced refresh republishes valid adapters on the next tick.
- Provider scheduling, registration, and shutdown share one lifecycle lock. During shutdown, controllers stop intake and close relevant UIs, provider bridges close/cancel, then the shared player queue rejects new work, drops pending coalesced reads, and drains accepted mutations for up to 10 seconds. A dispatch rejection cannot erase another accepted task.
- `config.yml` owns separate vault/active limits, unlock prices, eligibility nodes, and explicit OmniPet/LuckPerms/hybrid precedence. The LuckPerms permission template is expanded and validated through the configured active-slot maximum before activation. Exact legacy `globalMaxSlots`/`slotPermission` files are migrated atomically with `config.yml.bak`.

## Deferred roadmap

The following remain phased work: live provider/server certification, the Paper hatch start coordinator, online incubation checkpoints/scheduler, join/quit/crash recovery execution, hatch commands/GUI, live claim orchestration, live renderers, MythicMobs execution, ModelEngine runtime integration, owner MythicLib buffs, and progression gameplay. See [the roadmap](docs/roadmap.md) for ownership and release gates.

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

The single release artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar` (copied from `omnipet-paper/build/libs/`). The 2026-08-01 clean Paper egg/PDC/escrow bridge checkpoint passed 76 suites/273 tests: core 39 suites/157 tests and Paper 37 suites/116 tests, with zero failures, errors, or skips. It produced a 956,560-byte JAR with SHA-256 `EF231E023A2D22F703ACBB11BFEFC8E918B2A68D66009B235504694A1136D3C6`: 627 entries, 553 classes, one `paper-plugin.yml`, and zero Maven entries. This checkpoint did not rerun compatibility probes or live-server certification. Maven commands, `pom.xml`, Maven wrappers, and the old `passivepet2` artifact name are not part of the supported workflow.

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

Configuration and slot-provider sections identify their current authoritative contracts. The schema 1 egg example documents a verified core contract only; Paper gameplay, renderer, trigger, and other future examples remain non-authoritative until their owning phases ship.

The public [GitHub Pages manual](https://salyys1.github.io/OmniPet/) is deployed from `main`. Changes in this working branch are not public until merged and deployed successfully. The repository wiki is enabled but has no initial page yet, so repository docs remain authoritative.
