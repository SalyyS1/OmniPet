# Compatibility

Current compatibility evidence consists of Java/Gradle tests and Paper API compile probes. It does not include a live Paper server smoke test, gameplay certification, or vendor-plugin certification.

Verification snapshot: 2026-08-02. The clean JDK 21 Gradle build passed all 18 tasks and 132 suites/461 tests with zero failures, errors, or skips. Compatibility probes were not rerun for this checkpoint.

## Java baseline

OmniPet compiles with Java 21 and emits Java 21 bytecode. Paper 1.21.x probes use a Java 21 toolchain. Paper 26.x probes use a Java 25 compiler because that Paper line requires Java 25, while the OmniPet compile task retains `--release 21`.

## Exact compile matrix

| Paper API coordinate | Probe Java | Status | Claim allowed |
| --- | ---: | --- | --- |
| `1.21-R0.1-SNAPSHOT` | 21 | Passed | Primary compile baseline only. |
| `1.21.11-R0.1-SNAPSHOT` | 21 | Passed | Additional 1.21.x compile probe only. |
| `26.1.1.build.29-alpha` | 25 | Passed | Preview/alpha compile evidence for this exact coordinate. |
| `26.1.2.build.74-stable` | 25 | Passed | Experimental compile evidence for this exact coordinate. |
| `26.2.build.87-stable` | 25 | Passed | Forward compile guard for this exact coordinate. |

Do not convert these rows into `1.21.x supported`, `26.1.1+ supported`, or `latest supported`. `api-version: '1.21'` is a descriptor contract, not a binary/runtime guarantee.

## Current runtime scope

The current Paper code loads repositories, migrates bounded storage config, journals legacy eggs, builds a definition snapshot, registers `/pet` with `/pets` alias, and exercises Admin Pet Studio, the player vault, provider-choice slot menus, transaction reconciliation, and the escrow-gated 27-slot hatch GUI/start/tick/recovery/claim flow. Hatch timing begins only after committed escrow is observed; failed persisted ticks retain elapsed online time, and bounded pending recovery runs after start failures, pending ticks, and joins. Purchase-journal schema 1 completion states are conservatively reopened for entitlement verification without replaying economy calls. Provider disable events invalidate only the affected Vault/service-owner, PlayerPoints, or LuckPerms adapter; refresh is coalesced to the next tick. The JAR also contains dependency-neutral schema 4 incubation, deterministic hatch, schema 1 egg catalog, item-escrow contracts, and an in-repo persisted-state listener seam. These paths have compile/unit evidence but no live-server smoke certification. Live entities, renderers, skills, reducer/instant items, hard MMOItems integration, and a separately versioned public API remain deferred.

## Optional integrations

| Integration | Current state | Compatibility position |
| --- | --- | --- |
| MythicLib/MMOItems | Reflection-safe MythicLib Studio catalog; MMOItems only affects optional capability fingerprinting and has no item creation/redemption bridge | GUI/catalog compile-tested only; hatch outcomes remain provider-neutral snapshots, while live vendor smoke, hard MMOItems integration, and runtime buff application remain deferred. |
| MythicMobs skills | Not implemented | Deferred to the skill phase. |
| ModelEngine | Domain provider value exists; no renderer adapter | Deferred; no runtime or 26.x claim. |
| Vault/PlayerPoints/LuckPerms | Reflection-safe adapters, balances, explicit selection/confirm, dynamic lifecycle, entitlement precedence, and audited recovery implemented | Unit/compile evidence only; no exact provider or live runtime compatibility claim. |

Economy and LuckPerms use dedicated dynamic registries that re-probe plugin, service, and ABI availability. `OptionalAdapterLoader` proves a separate fail-closed reflection/linkage seam; it does not activate a vendor integration.

## Release certification gate

Before advertising an exact Paper build, run at least:

1. Clean boot and disable on the exact Paper/Java pair.
2. `/pet` and `/pets` permission behavior.
3. Valid, invalid, archived, duplicate, and legacy definition loads.
4. Player migration, `.bak`, quarantine, restart, and explicit recovery.
5. Every shipped gameplay system once its phase exists.
6. Every optional integration permutation advertised by the release.
7. Paper hatch exact-hand removal, commit-gated online-time checkpoints, bounded restart recovery, and capacity-safe claim.

Record exact build strings and test dates. Passing compilation remains necessary but insufficient.
