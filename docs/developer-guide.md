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

- Schema 2 envelopes: `PlayerStateEnvelope` and `PetDefinitionEnvelope`.
- Stable identities: player UUID, per-instance pet UUID, stable definition ID, and optimistic revision.
- Definition model: tier `D/C/B/A/S`, mandatory head icon, display provider contract, raw YAML node, and finite statistic bounds.
- Repositories: player state locking/revision checks, YAML pet definition read/save/archive/reference scan, and immutable registry snapshots.
- Migration: schema 1 player/pet readers, deterministic pet-instance UUID assignment, legacy egg journal, and legacy `passivepet` item-key constants.
- Storage safety: canonical containment, Windows-safe IDs, case-fold collision checks, no symbolic links, atomic replace, `.bak`, archive, and quarantine.

Unknown top-level fields, pet-instance fields, component maps, current egg data, and definition raw nodes are copied into immutable raw maps and written back. This preservation is intentional: later phases may understand data that Phase 1 does not.

## Paper bootstrap and Studio

`io.github.salyvn.omnipet.paper.OmniPetPlugin` wires the foundation and Studio:

1. Reject a symbolic-link data folder.
2. Create the data root.
3. Journal legacy `eggs.yml` if present.
4. Construct pet/player repositories and load a registry snapshot.
5. Create the shared Studio save/reload transaction and session manager.
6. Register the guarded Studio listener and `/pet` with `/pets` as alias.
7. Disable the plugin on initialization failure.

`/pet admin browse` opens the tokenized Studio tier/list/editor flow. Save/archive and `/pet admin reload` use the same staged registry generation boundary. Hatching, player menus, renderers, vendor adapters, and economy services remain later phases.

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

The Phase 2 validation passed `gradlew.bat test --no-daemon --rerun-tasks` on JDK 21 with 89/89 tests: 58 core and 31 Paper. The single artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar`, authored by `SalyVn`, with no bundled Paper/vendor packages or `META-INF/maven` entries.

There is no live Paper server smoke-test evidence yet. Treat runtime certification and every optional integration as later release gates.

## Deferred extension surfaces

Incubation, multi-pet/vault slots, renderers, MythicMobs skills, ModelEngine, economy adapters, and a published addon API remain phase-owned roadmap work. Any new mutation must reuse `RegistrySnapshotTransaction` and preserve unknown raw nodes.
