# Developer guide

OmniPet is a Java 21, Gradle Kotlin DSL, multi-project rewrite. The authoritative implementation lives in `omnipet-core` and `omnipet-paper`; the older root `src/` tree remains migration and behavior reference, not the shipped gameplay authority.

## Build contract

```text
# Windows
gradlew.bat clean build

# Linux/macOS
./gradlew clean build
```

- Gradle Wrapper 9.1.0 is pinned with a distribution SHA-256.
- Group: `io.github.salyvn`.
- Version: `3.0.0-SNAPSHOT` unless `pluginVersion` is overridden.
- Java toolchain and `--release`: 21.
- Encoding: UTF-8.
- Maven metadata, wrappers, and `target` workflow directories are rejected by the build.

## Module boundary

```text
omnipet-core  ->  omnipet-paper  ->  OmniPet-<version>.jar
domain/schema     Paper bootstrap    installable distribution
persistence
migration
```

`omnipet-paper` depends on `omnipet-core`. The core module depends on SnakeYAML only and the build rejects Bukkit, Paper, MythicLib, MMOItems, MythicMobs, and ModelEngine imports from core. The Paper module owns the descriptor, lifecycle command registration, compatibility compile task, and distribution JAR.

Do not add vendor modules until an owning phase proves a real API/classloader boundary.

## Current core contracts

- Schema 3 player envelopes and schema 2 pet-definition envelopes: `PlayerStateEnvelope` and `PetDefinitionEnvelope`.
- Stable identities: player UUID, per-instance pet UUID, stable definition ID, and optimistic revision.
- Definition model: tier `D/C/B/A/S`, mandatory head icon, display provider contract, raw YAML node, and finite statistic bounds.
- Repositories: player state locking/revision checks, YAML pet definition read/save/archive/reference scan, and immutable registry snapshots.
- Migration: schema 1/2 player readers, deterministic pet-instance UUID assignment, one-time vault/active-intent conversion, legacy egg journal, and legacy `passivepet` item-key constants.
- Storage safety: canonical containment, Windows-safe IDs, case-fold collision checks, no symbolic links, atomic replace, `.bak`, archive, and quarantine.

Unknown top-level fields, pet-instance fields, component maps, current egg data, slot-entitlement fields, and definition raw nodes are copied into immutable raw maps and written back. This preservation is intentional: later phases may understand data that Phase 1 does not.

## Paper bootstrap and Studio

`io.github.salyvn.omnipet.paper.OmniPetPlugin` wires the foundation and Studio:

1. Reject a symbolic-link data folder.
2. Create the data root.
3. Journal legacy `eggs.yml` if present.
4. Load or atomically migrate the bounded Phase 4 storage config.
5. Construct pet/player repositories and load a registry snapshot.
6. Create the shared Studio transaction, cached MythicLib catalog, and player storage controller.
7. Register guarded Studio/player lifecycle listeners and `/pet` with `/pets` as alias.
8. Reconcile online-player storage intent asynchronously; disable the plugin on initialization failure.

`/pet admin browse` opens the tokenized Studio tier/list/editor flow. The stat picker reads MythicLib through its own plugin class loader, caches by registry generation, vendor fingerprint, and refresh epoch, and fails closed to manual IDs when the provider is absent, disabled, or incompatible. Save/archive, exact-ID hard delete, clone-only authoring, and `/pet admin reload` use the same staged registry generation boundary; active Studio sessions block removal references.

`/pet [page]` opens the player vault from canonical schema 3 storage. View/reconcile reads are coalesced, the controller admits one mutation per player, and repository work is serialized per UUID on Paper async workers. Permissions are resolved before dispatch; render, message, open/close, and next-tick click transitions stay on the main thread. Request generations plus inventory context prevent stale tasks from replacing a newer Studio or external inventory. Disable stops new work, drops pending reads, drains accepted mutations, and waits up to 10 seconds for active storage work. Durable root+UUID file locks keep a rare straggler serialized with a subsequent classloader/enable. Hatching, runtime stat application, renderers, provider-backed purchase controls, and vendor economy adapters remain later slices.

## Compatibility probes

Run the normal baseline with Java 21:

```text
gradlew.bat clean build
```

The Paper boundary can be compiled against an exact API coordinate:

```text
gradlew.bat compileCompatibilityJava -PcompatibilityPaperApiVersion=1.21.11-R0.1-SNAPSHOT -PcompatibilityJavaVersion=21
```

For 26.x, supply a Java 25 toolchain and an exact alpha/stable coordinate. A successful compile does not prove plugin boot, command behavior, scheduler safety, entity behavior, or vendor compatibility.

## Verification evidence

The 2026-07-31 checkpoint clean build passed 165/165 tests: 99 core and 66 Paper. Exact compatibility probes passed for Paper `1.21.11-R0.1-SNAPSHOT` on Java 21 and `26.1.1.build.29-alpha` on Java 25; earlier probes also passed `26.1.2.build.74-stable` and `26.2.build.87-stable`. The single artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar` (713,156 bytes, SHA-256 `EE3B401947873A112F262053176EC3609FD34CFB1B4DA4C692108FC2DCFB7E1E`), authored by `SalyVn`, with no bundled Paper/vendor packages or `META-INF/maven` entries.

There is no live Paper server smoke-test evidence yet. Treat runtime certification and every optional integration as later release gates.

## Deferred extension surfaces

Incubation, live multi-pet renderers, MythicMobs skills, ModelEngine, economy adapters/UI, and a published addon API remain phase-owned roadmap work. Pet ownership/activation mutations must use `PetStorageService`; definition mutations must reuse `RegistrySnapshotTransaction`; both preserve unknown raw nodes.
