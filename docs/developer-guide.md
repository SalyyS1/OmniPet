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

- Schema 4 player envelopes, schema 2 pet-definition envelopes, and schema 1 egg-definition envelopes: `PlayerStateEnvelope`, `PetDefinitionEnvelope`, and `EggDefinitionEnvelope`.
- Stable identities: player UUID, per-instance pet UUID, stable definition ID, and optimistic revision.
- Definition model: tier `D/C/B/A/S`, mandatory head icon, display provider contract, raw YAML node, and finite statistic bounds.
- Repositories: player state locking/revision checks, YAML pet definition read/save/archive/reference scan, and immutable registry snapshots.
- Migration: schema 1-3 player readers, deterministic pet-instance UUID assignment, one-time vault/active-intent conversion, raw legacy `currentEgg`/pre-v4 `incubation` preservation, legacy egg journal, and legacy `passivepet` item-key constants.
- Storage safety: canonical containment, Windows-safe IDs, case-fold collision checks, no symbolic links, atomic replace, `.bak`, archive, and quarantine.
- Incubation: deterministic `splitmix64-v1` outcome resolution plus pure start/tick/reduce/set/complete/cancel/claim transitions. Claim runs inside the player repository lock, remains atomic with vault admission, and leaves a full-vault outcome `READY`.
- Egg catalog: `YamlEggDefinitionRepository` loads schema 1 one-file-per-egg definitions with matching IDs, bounded duration/candidates, a 64 KiB file cap, and a 10,000-entry scan bound.
- Item escrow: `ItemEscrowService` and `FileEggEscrowJournal` provide exactly-once create/compare-transition behavior across journal instances. Transaction UUID equals incubation UUID; item identity includes hand, inventory slot, nonce, fingerprint, and expected amount. Ambiguous recovery fails closed to operator review.

Unknown top-level fields, pet-instance fields, component maps, current egg data, pre-v4 incubation nodes, slot-entitlement fields, and definition/egg raw nodes are copied into immutable raw maps and written back. This preservation is intentional: later phases may understand data that the current checkpoint does not. Unresolved legacy egg/incubation data blocks a new core incubation start.

## Paper bootstrap and Studio

`io.github.salyvn.omnipet.paper.OmniPetPlugin` wires the foundation and Studio:

1. Reject a symbolic-link data folder.
2. Create the data root.
3. Journal legacy `eggs.yml` if present.
4. Load or atomically migrate the bounded Phase 4 storage config.
5. Construct pet/player repositories and load a registry snapshot.
6. Create the shared Studio transaction, player storage controller, purchase journal, and dynamic provider registries.
7. Register guarded Studio/player/provider lifecycle listeners and `/pet` with `/pets` as alias.
8. Reconcile online-player storage intent asynchronously; disable the plugin on initialization failure.

`/pet admin browse` opens the tokenized Studio tier/list/editor flow. The stat picker reads MythicLib through its own plugin class loader, caches by registry generation, vendor fingerprint, and refresh epoch, and fails closed to manual IDs when the provider is absent, disabled, or incompatible. Save/archive, exact-ID hard delete, clone-only authoring, and `/pet admin reload` use the same staged registry generation boundary; active Studio sessions block removal references.

`/pet [page]` opens the player vault from canonical schema 4 storage. View/reconcile reads are coalesced, the controller admits one mutation per player, and repository work is serialized per UUID on Paper async workers. Permissions are resolved before dispatch; render, message, open/close, and next-tick click transitions stay on the main thread. Request generations plus inventory context prevent stale tasks from replacing a newer Studio or external inventory.

`/pet slot` uses a separate selection/confirmation holder. The displayed amount is retained in the holder, revalidated against the live config, and combined with one transaction UUID. Vault/PlayerPoints adapters are resolved for every call, execute through a cancellable main-thread bridge, and return proven success/failure or an ambiguous result. Purchase and admin reconciliation share the same transaction coordinator. Required LuckPerms propagation is a durable final saga step and can be retried with `/pet admin reconcile <transaction-uuid> sync` after restart.

Purchase journals write schema 2. On read, schema 1 `COMPLETED`, `ENTITLEMENT_PERSISTED`, and `ENTITLEMENT_SYNC_PENDING` rows normalize to `ENTITLEMENT_SYNC_PENDING` with external entitlement verification required. Financial evidence is preserved and no withdrawal/refund is replayed. The sync path verifies the local OmniPet entitlement before idempotent external sync; a successful save rewrites schema 2 atomically and retains `.bak`. Journal discovery is cursor-paged without sorting or loading the full directory, caps each file read at 16 KiB, and reports at most 20 issue details plus omission/truncation metadata.

Provider disable handling is dependency-aware: Vault and its registered economy service owner invalidate only `VAULT`, PlayerPoints invalidates only `PLAYER_POINTS`, and LuckPerms invalidates only entitlement sync. Repeated lifecycle events coalesce to one next-tick refresh that atomically republishes adapters whose plugin/service/ABI probes pass. Full OmniPet disable invalidates all adapters, cancels/rejects provider calls, drains accepted mutations, and waits up to 10 seconds. The incubation/catalog/escrow core is not wired here: Paper item/PDC removal, online checkpoints, recovery execution, commands/GUI, live claim orchestration, runtime stat application, and renderers remain later slices.

## Compatibility probes

Run the normal baseline with Java 21:

```text
gradlew.bat clean build
```

The Paper boundary can be compiled against an exact API coordinate:

```text
.\gradlew.bat :omnipet-paper:compileCompatibilityJava `
  '-PcompatibilityPaperApiVersion=1.21.11-R0.1-SNAPSHOT' `
  '-PcompatibilityJavaVersion=21' `
  --no-daemon --console=plain
```

For a 26.x probe, run Gradle with a JDK 25 `JAVA_HOME` and use an exact alpha/stable coordinate:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat :omnipet-paper:compileCompatibilityJava `
  '-PcompatibilityPaperApiVersion=26.1.1.build.29-alpha' `
  '-PcompatibilityJavaVersion=25' `
  --no-daemon --console=plain
```

A successful compile does not prove plugin boot, command behavior, scheduler safety, entity behavior, or vendor compatibility.

## Verification evidence

The 2026-07-31 clean Phase 3 landing build passed 65 suites/239 tests: 157 core and 82 Paper, with zero failures, errors, or skips. All five exact workflow coordinates passed compile probes. The single artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar` (930,379 bytes, SHA-256 `D2F14E803BF5FCCB1D6B1FCEB22130D926C76BD2AB4BCC006E5D3C3CD0EF0109`), with 611 entries, 539 classes, one `paper-plugin.yml`, and zero forbidden bundled entries.

There is no live Paper server smoke-test evidence yet. Treat runtime certification and every optional integration as later release gates.

## Deferred extension surfaces

Paper incubation orchestration, live multi-pet renderers, MythicMobs skills, ModelEngine, live vendor certification, and a published addon API remain phase-owned roadmap work. Pet ownership/activation mutations must use `PetStorageService`; definition mutations must reuse `RegistrySnapshotTransaction`; economy mutations must use the durable slot saga and never call a provider before journaling external intent. Future Paper hatch start must persist escrow `PREPARED` and the resolved incubation before removing exactly one revalidated item, then advance through `ITEM_REMOVED` to `COMMITTED`.
