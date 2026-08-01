# Changelog

## Unreleased - OmniPet 3.0 development line

### Added

- Added player schema 4 with a typed singular incubation state, deterministic `splitmix64-v1` hatch outcomes, bounded action-token idempotency, and capacity-safe atomic claim behavior.
- Added schema 1 egg-definition codecs/repository for one-file-per-egg catalogs with duration parsing, weighted candidates, case-safe IDs, and bounded file/catalog reads.
- Added the dependency-neutral item-escrow saga with atomic exactly-once create/compare-transition behavior, stable item identity, bounded journal reads/scans, and conservative recovery directives.
- Added the Paper paid-start saga, UUID-safe join recovery executor, stable cancellation tokens, and a shared-queue monotonic online checkpoint coordinator.

### Changed

- Rebranded the public project, descriptor, examples, and operator documentation as OmniPet by SalyVn.
- Switched the documented build workflow from Maven to Gradle Kotlin DSL.
- Added Paper 1.21.x/Java 21 and Paper 26.1+/Java 25 compatibility guidance with an explicit alpha/experimental caveat for 26.x.
- Added a static GitHub Pages workflow that publishes `docs/` without repository secrets.
- Added migration guidance for legacy `passivepet` permissions, PDC keys, MMOItems stat IDs, data folders, and stable YAML fields.
- Added dual-read documentation for legacy item identifiers and optional MythicLib/MMOItems integrations.
- Unified vault and slot-purchase work behind one plugin-owned per-player queue, with namespaced pending-read coalescing and FIFO cross-controller serialization; hardened lifecycle shutdown ordering, accepted-mutation draining, and dispatch-rejection isolation.
- Updated clean-build evidence to the current Gradle verification checkpoint: 79 suites/276 tests (core 39/157, Paper 40/119), zero failures/errors/skips, a 977,517-byte artifact, and zero Maven entries. Compatibility probes, crash-injection proof, and live-server certification were not rerun for this checkpoint.

### Compatibility notes

- Paper 1.21.x is the primary release line.
- Paper 26.1.1 artifacts observed during research were alpha builds; no blanket `26.1.1+` support claim is made.
- Paper 26.1+ requires Java 25. MythicLib, MMOItems, MythicMobs, and ModelEngine vendor support must be verified separately.
- ModelEngine and Paper display-entity rendering remain roadmap adapter boundaries; no live renderer is shipped in this checkpoint.
- Paper incubation now includes the paid-start saga, monotonic online checkpoints, and conservative join recovery. Hatch commands, GUI/claim presentation, crash-injection proof, and live claim orchestration remain deferred.

### Migration notes

- Keep `globalMaxSlots`, `slotPermission`, `duration`, `rarity`, `pets`, and component IDs unchanged when migrating existing YAML.
- Existing `passivepet:*` item PDC keys and `PASSIVEPET_*` MMOItems stat IDs are treated as legacy compatibility identifiers. New items use OmniPet identifiers.
- Back up `plugins/PassivePet/` and `plugins/OmniPet/` before switching descriptors or copying data. Resolve conflicts manually; never merge two live player-data directories blindly.
- Player schemas 1-3 migrate to schema 4 while preserving raw `currentEgg` and pre-v4 top-level `incubation` data. Root `eggs.yml` stays migration input; canonical core definitions use `plugins/OmniPet/eggs/*.yml`.

## Previous releases

The historical PassivePet release notes were not present in this repository snapshot. Add versioned entries here as releases are cut; do not invent prior behavior or support claims.
