# Changelog

## Unreleased - OmniPet 3.0 development line

### Added

- Added a unified player hub: `/pet` with no arguments now opens it, with Vault, Hatch, Active slots, Help, and (staff only) Studio tiles, each showing a live summary from one coalesced storage read. `/pet <page>` and the new `/pet vault [page]` keep direct vault access; vault, hatch, and management gained back-to-hub controls.
- Added `/pet help [page]` and permission-filtered tab-complete, both generated from one declarative command spec so they cannot drift from the dispatcher. Grouping literals such as `admin` only appear when the sender holds one of their child permissions.
- Added `messages.yml`: player-facing chat, GUI titles, and GUI lore are now operator-editable with MiniMessage, generated from the built-in defaults on first start, and reloaded by `/pet admin reload`. Unknown keys warn, missing keys fall back, broken tags render literally, and the file never disables the plugin. Operator audit output stays in the plugin.
- Added a shared `GuiItems` builder: no OmniPet menu renders italic lore any more, lore is grouped with a consistent colour grammar, and titles carry location context.
- Added Studio input fixes: every chat field now prompts with format, example, and the `cancel` hint; clicking a stat shows one explained button per supported modifier before asking for `min max` (the full `FLAT 10 50` form still works); the head-icon field auto-detects a pasted base64 payload, an `http(s)` URL, or a bare 64-character texture hash while keeping the legacy `<SOURCE> <value>` form.
- Added player schema 4 with a typed singular incubation state, deterministic `splitmix64-v1` hatch outcomes, bounded action-token idempotency, and capacity-safe atomic claim behavior.
- Added schema 1 egg-definition codecs/repository for one-file-per-egg catalogs with duration parsing, weighted candidates, case-safe IDs, and bounded file/catalog reads.
- Added the dependency-neutral item-escrow saga with atomic exactly-once create/compare-transition behavior, stable item identity, bounded journal reads/scans, and conservative recovery directives.
- Added the Paper paid-start saga, UUID-safe join recovery executor, stable cancellation tokens, and a shared-queue monotonic online checkpoint coordinator.
- Added player-facing `/pet hatch` commands and a durable hatch GUI with exact main/off-hand capture, countdown refresh, escrow-committed gating, and capacity-safe READY claim presentation.
- Added the in-repo `HatchEvent`/`HatchEventListener` seam for successful persisted core state changes. Events carry `DeliveryStage.STATE_PERSISTED`; unchanged mutations emit no notification, observer failures are isolated, and a `STARTED` notification is not egg escrow/payment proof.

### Changed

- `/pet` with no arguments now opens the player hub instead of vault page 1. `/pet <page>` and `/pet vault [page]` preserve direct vault access.
- Rebranded the public project, descriptor, examples, and operator documentation as OmniPet by SalyVn.
- Switched the documented build workflow from Maven to Gradle Kotlin DSL.
- Added Paper 1.21.x/Java 21 and Paper 26.1+/Java 25 compatibility guidance with an explicit alpha/experimental caveat for 26.x.
- Added a static GitHub Pages workflow that publishes `docs/` without repository secrets.
- Added migration guidance for legacy `passivepet` permissions, PDC keys, MMOItems stat IDs, data folders, and stable YAML fields.
- Added dual-read documentation for legacy item identifiers and optional MythicLib/MMOItems integrations.
- Unified vault and slot-purchase work behind one plugin-owned per-player queue, with namespaced pending-read coalescing and FIFO cross-controller serialization; hardened lifecycle shutdown ordering, accepted-mutation draining, and dispatch-rejection isolation.
- Hardened hatch timing and recovery: the monotonic baseline begins only when committed escrow is observed, failed persisted ticks accumulate elapsed online time, and start-failure/tick/join recovery uses bounded pending scans with explicit operator-review warnings.
- Updated clean-build evidence to the current JDK 21 Gradle verification checkpoint: 18/18 tasks, 147 suites/605 tests (core 63/256, Paper 84/349), zero failures/errors/skips, a 1,674,717-byte artifact, and zero Maven or bundled MiniMessage entries. Compatibility probes, crash-injection proof, and live-server certification were not rerun for this checkpoint.

### Compatibility notes

- Paper 1.21.x is the primary release line.
- Paper 26.1.1 artifacts observed during research were alpha builds; no blanket `26.1.1+` support claim is made.
- Paper 26.1+ requires Java 25. MythicLib, MMOItems, MythicMobs, and ModelEngine vendor support must be verified separately.
- ModelEngine and Paper display-entity rendering remain roadmap adapter boundaries; no live renderer is shipped in this checkpoint.
- Paper incubation now includes the paid-start saga, monotonic online checkpoints, conservative join recovery, hatch commands, and escrow-gated GUI claim presentation. Crash-injection proof and live-server certification remain deferred.
- Reducer/instant-hatch items, native item distribution, hard MMOItems integration, and a separately versioned public addon API remain deferred. Any consumable hatch item must add durable redemption/escrow; an action token or core state notification alone is insufficient settlement proof.

### Migration notes

- Keep `globalMaxSlots`, `slotPermission`, `duration`, `rarity`, `pets`, and component IDs unchanged when migrating existing YAML.
- Existing `passivepet:*` item PDC keys and `PASSIVEPET_*` MMOItems stat IDs are treated as legacy compatibility identifiers. New items use OmniPet identifiers.
- Back up `plugins/PassivePet/` and `plugins/OmniPet/` before switching descriptors or copying data. Resolve conflicts manually; never merge two live player-data directories blindly.
- Player schemas 1-3 migrate to schema 4 while preserving raw `currentEgg` and pre-v4 top-level `incubation` data. Root `eggs.yml` stays migration input; canonical core definitions use `plugins/OmniPet/eggs/*.yml`.

## Previous releases

The historical PassivePet release notes were not present in this repository snapshot. Add versioned entries here as releases are cut; do not invent prior behavior or support claims.
