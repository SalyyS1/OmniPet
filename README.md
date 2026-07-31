# OmniPet

OmniPet is a Paper plugin rewrite. This checkout is the verified Gradle-only foundation plus Admin Pet Studio and the Phase 4 player-storage/economy slice: versioned contracts, tokenized GUIs, persisted vault/active intent, explicit-currency slot purchases, durable recovery journals, and reflection-safe optional provider adapters. Incubation and live pet entities remain phased work.

## Shipped foundation and Studio

- Gradle Wrapper 9.1.0 is the only supported build authority. The clean verification command is `gradlew.bat clean build` on Windows or `./gradlew clean build` on Linux/macOS.
- `omnipet-core` contains dependency-neutral domain, YAML codecs, migration readers, atomic storage, path checks, and repository/registry seams. `omnipet-paper` contains the Paper bootstrap and produces the installable distribution JAR.
- Java 21 is the source and release baseline. Resources and Java compilation use UTF-8.
- The descriptor is branded `OmniPet`, authored by `SalyVn`, and loads `io.github.salyvn.omnipet.paper.OmniPetPlugin`.
- Paper registers `/pet` with `/pets` as its alias. Both names expose the phased player entry point and the live `/pet admin browse` Studio branch.
- `omnipet.general` defaults to `true`; `omnipet.*` defaults to `op`; declared `omnipet.admin.*` nodes default to `false`.
- Player envelopes use schema version 3 and pet definitions use schema version 2. The core preserves raw unknown nodes, stable IDs, canonical vault/active intent, legacy egg state, and recoverable orphan data instead of silently replacing a profile.
- Atomic writes create `.bak` snapshots when replacing an existing file. Invalid player files are quarantined; a quarantined profile fails closed until an operator performs explicit recovery.
- Legacy egg definitions are validated and written to `migration/legacy-eggs-v1.yml` with a semantic SHA-256 journal. The source `eggs.yml` is never overwritten by that journal.
- `/pet admin browse` and `/pets admin browse` open the D/C/B/A/S Studio tier browser for `omnipet.admin.managepet` staff. The Studio supports paginated definition lists, a reflection-safe MythicLib stat picker with manual fallback, create/edit drafts, typed rarity/progression/skill/release fields, archive mode, safe inventory events, chat input expiry/cancel, optimistic conflict checks, and atomic Save/reload generation swaps.
- `/pet [page]` and `/pets [page]` open the UUID/revision-safe player vault. View/reconcile reads are coalesced per player, one mutation is admitted at a time, and repository work is serialized off-thread; Bukkit permissions and inventory operations remain on the main thread.
- `/pet slot` opens the next-slot purchase flow. Vault and PlayerPoints prices are separate choices with balance display and confirmation; no currency is auto-selected. Unknown provider outcomes remain reconciliation-gated.
- `/pet admin transactions [limit] [cursor]` and `/pet admin reconcile <transaction-uuid> <charge|no-charge|refund|sync>` expose bounded, audited recovery without blindly replaying economy calls. The optional cursor is an opaque continuation token printed by OmniPet and must be reused verbatim. Each journal file is capped at 16 KiB; a page reports at most 20 unreadable issues plus omission/truncation summaries. Failed LuckPerms propagation remains durably `ENTITLEMENT_SYNC_PENDING` until an idempotent `sync` retry succeeds.
- Legacy purchase-journal schema 1 rows in `COMPLETED`, `ENTITLEMENT_PERSISTED`, or `ENTITLEMENT_SYNC_PENDING` are conservatively reopened as `ENTITLEMENT_SYNC_PENDING`. OmniPet does not replay the economy operation; successful `sync` verification rewrites schema 2 atomically and retains the schema 1 file as `.bak`.
- Provider lifecycle changes invalidate only the affected Vault/service-owner, PlayerPoints, or LuckPerms adapter. Healthy unrelated providers remain available while one coalesced refresh republishes valid adapters on the next tick.
- `config.yml` owns separate vault/active limits, unlock prices, eligibility nodes, and explicit OmniPet/LuckPerms/hybrid precedence. The LuckPerms permission template is expanded and validated through the configured active-slot maximum before activation. Exact legacy `globalMaxSlots`/`slotPermission` files are migrated atomically with `config.yml.bak`.

## Deferred roadmap

The following remain phased work: live provider/server certification, incubation/hatching gameplay, live renderers, MythicMobs execution, ModelEngine runtime integration, and progression gameplay. See [the roadmap](docs/roadmap.md) for ownership and release gates.

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
gradlew.bat clean build
```

The single release artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar` (copied from `omnipet-paper/build/libs/`). The 2026-07-31 post-fix landing build passed 54 suites/203 tests (121 core, 82 Paper) and produced an 831,926-byte JAR with SHA-256 `893C4D6C36CEBB072100B78895DB0FE9023AEA1C33D7F9BD025BE67EB3CDD970`: 557 entries, 487 classes, one descriptor, and zero forbidden bundled entries. Maven commands, `pom.xml`, Maven wrappers, and the old `passivepet2` artifact name are not part of the supported workflow.

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

Configuration and slot-provider sections identify their current authoritative contracts. Egg, renderer, trigger, and other future examples remain non-authoritative until their owning phases ship.

The public [GitHub Pages manual](https://salyys1.github.io/OmniPet/) is deployed from `main`. Changes in this working branch are not public until merged and deployed successfully. The repository wiki is enabled but has no initial page yet, so repository docs remain authoritative.
