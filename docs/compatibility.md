# Compatibility

> The [wiki](https://salyys1.github.io/OmniPet/wiki.html#/compatibility) is the reference for this topic. Where this file disagrees with it, the wiki is correct.

Current compatibility evidence consists of Java/Gradle tests and Paper API compile probes. It does not include a live Paper server smoke test, gameplay certification, or vendor-plugin certification.

Verification snapshot: 2026-08-03. The clean JDK 21 Gradle build passed all 18 tasks and 161 suites/699 tests with zero failures, errors, or skips. The `1.21`, `1.21.1`, and `1.21.11` compile probes were rerun for this checkpoint because two new Paper API surfaces were introduced and because a `Sound` API incompatibility was found in production on `1.21.11`; all three compile with zero warnings. The remaining rows were not rerun.

## Newly relied-upon Paper API surfaces

Two surfaces entered the compile boundary in the feedback and pet-click work. Both are named here because `Sound`'s shape in particular is the kind of thing that shifts across Paper lines, and a silent dependency on it would be discovered by an operator rather than by a probe.

| Surface | Used by | Shape verified against `1.21`, `1.21.1`, and `1.21.11` |
| --- | --- | --- |
| `org.bukkit.Registry.SOUNDS` | `feedback/SoundResolver` | Interface with an identical `get(NamespacedKey)` signature on all three probes. Sound names resolve through it once at startup; an unknown name warns and silences one feedback category rather than failing. |

**`org.bukkit.Sound` changed kind between Paper lines and must not be called statically.** It is an
`enum` on `1.21` and `1.21.1` but an `interface` from `1.21.11` onward. Code compiled as
`Sound.valueOf(name)` or `sound.getKey()` against the older API emits a `Methodref` constant; loading
that class on the newer line throws `IncompatibleClassChangeError: ... must be InterfaceMethodref
constant` and **the plugin fails to enable at all**. A compile probe does not catch this — compiling
against `1.21.11` succeeds with only a deprecation warning, because only the compiled reference kind
differs.

This bit us in production on Paper `1.21.11`. Two guards now exist: `SoundResolver` routes every lookup
through `Registry.SOUNDS`, and `SoundApiCompatibilityContractTest` walks the constant pool of every
compiled class and fails on any `Methodref` against `org/bukkit/Sound`. The guard was verified by
reintroducing the exact regression and confirming it fails.

Two further Paper types changed kind the same way and are **not** used by OmniPet: `org.bukkit.block.Biome`
and `org.bukkit.attribute.Attribute`. `Material`, `EntityType`, and `Statistic` did not.
| `PlayerInteractEntityEvent` | `gui/pet/PetInteractListener` | `PlayerInteractAtEntityEvent` extends it but overrides `getHandlers()` with its own `HandlerList` on both probes, so a handler registered for the plain event never receives the At-variant. Only the plain event is registered. |

Neither adds a bundled dependency: both are Paper/Adventure API already on the compile classpath, and `checkDistributionArtifact` confirms the JAR gained no new entries.

## Java baseline

OmniPet compiles with Java 21 and emits Java 21 bytecode. Paper 1.21.x probes use a Java 21 toolchain. Paper 26.x probes use a Java 25 compiler because that Paper line requires Java 25, while the OmniPet compile task retains `--release 21`.

## Exact compile matrix

| Paper API coordinate | Probe Java | Status | Claim allowed |
| --- | ---: | --- | --- |
| `1.21-R0.1-SNAPSHOT` | 21 | Passed | Primary compile baseline. Rerun 2026-08-03. |
| `1.21.1-R0.1-SNAPSHOT` | 21 | Passed | Rerun 2026-08-03 for the two new API surfaces. |
| `1.21.11-R0.1-SNAPSHOT` | 21 | Passed | Rerun 2026-08-03; the line where `Sound` becomes an interface. |
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
