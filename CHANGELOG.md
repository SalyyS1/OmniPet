# Changelog

## Unreleased - OmniPet 3.0 development line

### Added

- Added egg and pet distribution commands, closing a gap that made a Studio-created pet unreachable. Nothing minted eggs — the item codec could read and restore one but never sign a new one, and no command handed them out — so a saved definition had no route into a player's vault. `/pet admin egg give` now mints an egg carrying the canonical PDC identity, `/pet admin egg create` defines a catalog entry, and `/pet admin pet give` grants a pet directly for testing. Each egg occupies its own inventory slot because each carries its own nonce; stacking them would leave the escrow saga unable to tell which egg was paid for.
- Saving a pet in the Pet Studio now also writes `eggs/<definitionId>_egg.yml`, so a new pet is obtainable without hand-writing catalog YAML. An existing entry is never overwritten, since an operator may have tuned its duration or candidate pool. The write happens after the definition is saved, so a catalog failure cannot roll back or obscure the pet, and `gui.studio.autoCreateEgg: false` turns it off.
- The egg catalog repository gained a write path; it was previously read-only, which is why nothing could create an egg definition.
- Added audible and visual feedback for every state change. Activating a pet, claiming a hatch, buying a slot, a rejected purchase, and a blocked request are now distinguishable by sound instead of all being silent. Cues play to the acting player only — never to nearby players, which would make a spammable click a griefing vector — are rate-limited per player, and sit beside the existing chat message so text remains the accessible channel. `gui.feedback.enabled: false` turns everything off in one switch.
- Added an optional `gui:` section to `config.yml` owning feedback toggles and sounds, the vault page size, the help page length, and the Studio prompt timeout. Fourteen former Java literals now read from it. The section is lenient, following the `messages.yml` precedent rather than the strict `storage:` one: an unknown key warns, a bad value falls back per key, and an out-of-range page size clamps with a warning, because a typo in a display setting must never stop players using their pets. Deleting the section reproduces the previous behaviour exactly. **`gui.vault.petsPerPage`, `gui.help.linesPerPage`, and `gui.studio.promptTimeoutSeconds` are restart-only and documented as such; `gui.feedback.*` reloads live.**
- Added vault sort and filter. Five sort orders (favorites-first, level, rarity, name, recent) and four filters (all, favorites, active, stored) cycle on a click, in memory over the already-loaded snapshot with no extra repository read. Every comparator is total and ties on pet UUID, so no pet can appear on two pages or vanish. A vault with no pets, a filter matching nothing, and the last page now render three distinct messages where they previously looked identical or, for the last page, silently dropped the NEXT arrow.
- Added right-click pet interaction: right-clicking your own rendered pet opens its management screen. It consumes the activation index that already shipped, through a lookup that rejects stale renderer generations, and filters the off-hand event so one click opens exactly one screen. Another player's pet does nothing and a non-OmniPet entity is left uncancelled. Riding remains unimplemented.
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

- **Fixed** a startup crash on Paper 1.21.11. `org.bukkit.Sound` is an enum on Paper 1.21 and 1.21.1 but an interface from 1.21.11 onward, so the feedback layer's `Sound.valueOf(name)` compiled to a `Methodref` against the enum and threw `IncompatibleClassChangeError` at class-load time — the plugin failed to enable at all rather than merely losing its sounds. Sound names now resolve through `Registry.SOUNDS`, whose signature is identical on every probed line. A contract test walks the constant pool of every compiled class and fails on any non-interface reference to `org.bukkit.Sound`, so a compile probe passing is no longer mistaken for the call being safe.
- **Fixed** a main-thread stall in vault sorting found during verification. The comparators re-derived each pet's summary on every comparison rather than once per pet, which at the 100,000-pet vault capacity was a 1.4-second freeze — roughly 28 ticks — from one sort click. Sort keys now derive once; the same case drops to about 250ms and a realistic 1,000-pet vault to under 5ms, with ordering unchanged.
- **Fixed** the hub slot tile returning to the wrong screen. A slot purchase opened from the hub replayed `pet <returnPage>` on cancel, on success, and on a stale quote, silently relocating the player to vault page 1 instead of back to the hub. The return command now belongs to an explicit origin carried on the purchase holder, so all three return points agree.
- The hub gained the concurrent-open guard its four sibling controllers already had, released on every failure path so a failed open cannot lock it.
- Collapsed duplicated formatters into `text/Durations`: the countdown existed twice (hatch menu and hub tile) and the decimal formatter twice (management screen and Studio stat screens). Output is unchanged and pinned by a test written against the pre-migration behaviour. Seven copies of enum-to-text conversion — two more than an earlier audit found — now route through `Displays`; operator and audit strings keep their raw enum form.
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
