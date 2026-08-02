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
- Persisted-state events: `RepositoryHatchService` publishes structured `HatchEvent` values with `DeliveryStage.STATE_PERSISTED` to in-repo `HatchEventListener` observers only after the player-state save succeeds. Unchanged results emit nothing, listener failures are isolated, reentrant notifications remain FIFO, and `STARTED` reports state persistence rather than egg escrow/payment settlement.
- Egg catalog: `YamlEggDefinitionRepository` loads schema 1 one-file-per-egg definitions with matching IDs, bounded duration/candidates, a 64 KiB file cap, and a 10,000-entry scan bound.
- Item escrow: `ItemEscrowService` and `FileEggEscrowJournal` provide exactly-once create/compare-transition behavior across journal instances. Transaction UUID equals incubation UUID; item identity includes hand, inventory slot, nonce, fingerprint, and expected amount. Ambiguous recovery fails closed to operator review.

Unknown top-level fields, pet-instance fields, component maps, current egg data, pre-v4 incubation nodes, slot-entitlement fields, and definition/egg raw nodes are copied into immutable raw maps and written back. This preservation is intentional: later phases may understand data that the current checkpoint does not. Unresolved legacy egg/incubation data blocks a new core incubation start.

## Paper bootstrap, Studio, and incubation bridge

`io.github.salyvn.omnipet.paper.OmniPetPlugin` wires the foundation and Studio:

1. Reject a symbolic-link data folder.
2. Create the data root.
3. Journal legacy `eggs.yml` if present.
4. Load or atomically migrate the bounded Phase 4 storage config.
5. Construct pet/player repositories, load `plugins/OmniPet/eggs/*.yml`, bind `plugins/OmniPet/data/egg-escrow/*.yml`, and cache pet-to-egg references.
6. Load the pet registry snapshot.
7. Create the shared Studio transaction, one plugin-owned `PerPlayerTaskQueue`, the player storage and slot-purchase controllers, the purchase journal, and dynamic provider registries.
8. Register guarded Studio/player/hatch/provider lifecycle listeners and `/pet` with `/pets` as alias.
9. Reconcile online-player storage intent asynchronously; disable the plugin on initialization failure.

`/pet admin browse` opens the tokenized Studio tier/list/editor flow. The stat picker reads MythicLib through its own plugin class loader, caches by registry generation, vendor fingerprint, and refresh epoch, and fails closed to manual IDs when the provider is absent, disabled, or incompatible. Save/archive, exact-ID hard delete, clone-only authoring, and `/pet admin reload` use the same staged registry generation boundary; active Studio sessions block removal references.

`/pet [page]` opens the player vault from canonical schema 4 storage. The vault and slot-purchase controllers share the plugin-owned `PerPlayerTaskQueue`: accepted work runs FIFO per player across both controllers, while different players may run concurrently. Only pending reads with the same namespaced key coalesce (`vault:view` for vault reads and `slot:view` for slot reads). Reconciliation, mutations, and purchases are never coalesced. Repository work runs on Paper async workers; Bukkit inventory, permission, and provider work stays on the main thread. Request generations plus inventory context prevent stale tasks from replacing a newer Studio or external inventory.

`/pet slot` uses a separate selection/confirmation holder but submits player work through the same shared queue as the vault. The displayed amount is retained in the holder, revalidated against the live config, and combined with one transaction UUID. Vault/PlayerPoints adapters are resolved for every call, execute through a cancellable main-thread bridge, and return proven success/failure or an ambiguous result. Purchase and admin reconciliation share the same transaction coordinator. Required LuckPerms propagation is a durable final saga step and can be retried with `/pet admin reconcile <transaction-uuid> sync` after restart.

Purchase journals write schema 2. On read, schema 1 `COMPLETED`, `ENTITLEMENT_PERSISTED`, and `ENTITLEMENT_SYNC_PENDING` rows normalize to `ENTITLEMENT_SYNC_PENDING` with external entitlement verification required. Financial evidence is preserved and no withdrawal/refund is replayed. The sync path verifies the local OmniPet entitlement before idempotent external sync; a successful save rewrites schema 2 atomically and retains `.bak`. Journal discovery is cursor-paged without sorting or loading the full directory, caps each file read at 16 KiB, and reports at most 20 issue details plus omission/truncation metadata.

Provider disable handling is dependency-aware: Vault and its registered economy service owner invalidate only `VAULT`, PlayerPoints invalidates only `PLAYER_POINTS`, and LuckPerms invalidates only entitlement sync. Provider scheduling, registration, and shutdown share one lifecycle lock; repeated lifecycle events coalesce to one next-tick refresh that atomically republishes adapters whose plugin/service/ABI probes pass. Full OmniPet disable first stops controller intake and closes relevant UIs, then closes/cancels provider bridges. The shared queue then rejects new work, drops pending coalesced reads, and drains accepted mutations for up to 10 seconds. Dispatch rejection is isolated so it cannot erase another accepted task.

The Paper incubation bootstrap opens the canonical egg repository and escrow journal, then wires `PaperIncubationCoordinator`. `PaperIncubationStartSaga` captures a fresh item identity on the main thread, persists `PREPARED` escrow and the resolved incubation asynchronously, removes exactly one revalidated item on the main thread, and advances `ITEM_REMOVED -> COMMITTED`. `PaperIncubationRecoveryExecutor` prioritizes actionable non-terminal escrow stages within the journal bound, asks `EggEscrowRecoveryService` for a directive, and performs only conservative remove/cancel/commit/refund operations; reaching the bound logs an operator-review warning rather than hiding older pending records. `PaperIncubationCoordinator` owns one five-second monotonic online loop; it uses the shared per-player queue with plain `submit`, never `submitLatest`, and drops durable tick work when a player lifecycle epoch is no longer current. `PaperEggItemCodec` dual-reads `omnipet:egg` and legacy `passivepet:egg`, then writes `omnipet:egg`, `omnipet:item_nonce`, and schema-1 `omnipet:item_schema`. Its durable snapshot serializes an amount-one clone, stores the payload in escrow extensions, fingerprints it with SHA-256, and rejects payloads over 8 KiB.

`PaperPlayerEggInventory` scans storage slots plus off-hand and permits capture/removal/refund only while the owner is online and execution is on Paper's primary thread. Recovery is conservative: malformed/unsupported identity, duplicate nonce, material or fingerprint mismatch, or an amount that cannot prove exactly one removal is ambiguous and requires operator review. Commit observation establishes the monotonic timing baseline; the baseline advances only after a persisted tick, so failed ticks retain accumulated online time. Start failures, pending tick observations, and joins request recovery. Pending scans prioritize actionable stages and stop at the bounded per-player limit with an operator-review warning. The coordinator is wired into join/quit/disable lifecycle, but these unit/source contract tests do not certify live Paper runtime behavior, crash exactly-once delivery, or vendor integration.

The egg catalog/reference index is immutable for the lifetime of the current plugin instance. Manual `eggs/*.yml` changes require a stop/restart; `/pet admin reload` currently reloads pet definitions, not egg definitions.

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

The 2026-08-02 clean JDK 21 Gradle build completed successfully with all 18 tasks. It passed 132 suites/461 tests: core 62 suites/238 tests and Paper 70 suites/223 tests, with zero failures, errors, or skips. The single artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar` (1,589,478 bytes, SHA-256 `0DC987AED51CD28775627DE7491850AB5FF1684D5C1ACE83A33746BE19B92386`), with 976 entries, 888 classes, one `paper-plugin.yml`, and zero Maven entries. Compatibility probes, crash-injection tests, and live-server smoke were not rerun for this checkpoint.

There is no live Paper server smoke-test evidence yet. Treat runtime certification and every optional integration as later release gates.

## Deferred extension surfaces

Multi-pet renderers, MythicMobs skills, ModelEngine, hard MMOItems integration, native item distribution, reducer/instant-hatch items, live vendor certification, crash-injection proof, and a published addon API remain phase-owned roadmap work. The core hatch listener is an internal integration seam, not a separately versioned API artifact. Pet ownership/activation mutations must use `PetStorageService`; definition mutations must reuse `RegistrySnapshotTransaction`; economy mutations must use the durable slot saga and never call a provider before journaling external intent. The shipped hatch flow uses the Paper item/inventory seams: persist escrow `PREPARED` and the resolved incubation before removing exactly one revalidated item, then advance through `ITEM_REMOVED` to `COMMITTED`; countdown and claim stay locked until escrow is committed. Future consumable hatch items require durable redemption/escrow and cannot treat action-token idempotency or `HatchEvent` delivery as settlement proof.
