# Roadmap

This roadmap separates shipped foundations from planned work. Priority is directional, not a release promise. Every feature must preserve migration safety, optional-adapter isolation, main-thread rules, and measurable performance.

## Current foundation

- YAML pet components and expression-backed values.
- Egg duration/rarity with uniform pet pools.
- Pet storage GUI, summon/recall, leveling, evolution, stamina, and triggers.
- Standalone persistent-data items.
- Optional MythicLib owner buffs/skill expression and MMOItems identifiers.
- Paper display/interaction entity renderer.
- OmniPet/SalyVn branding, Gradle build, migration docs, and compatibility policy.

## Prioritized work

| Priority | Feature | Status | Main risk/acceptance gate |
| --- | --- | --- | --- |
| P0 | Atomic player saves, quarantine, backups, and bounded autosave | Shipped foundation | Atomic writes, `.bak` snapshots, corrupt-file quarantine, and six persistence regression tests are present. |
| P0 | Versioned player/config schema and idempotent migration journal | Planned hardening | Migration must produce backup, report, and rollback path. |
| P0 | Runtime bug/validation sweep | Baseline shipped | Interval scheduling, movement boundaries, pagination, GUI sizes, invalid IDs, and optional-hook cleanup have deterministic checks or guards. |
| P1 | Egg tiers and weighted loot pools | Planned | Existing `duration`, `rarity`, and `pets` configs need a deterministic importer. |
| P1 | Pet quality/seed rolls | Planned | Reloads must not change an in-progress hatch outcome. |
| P1 | Event-driven MythicLib passive stats | Planned refactor | Stable modifier IDs and idempotent cleanup; no unconditional polling. |
| P1 | Active skill dispatcher with cooldown/stamina transaction | Planned | Missing providers must not consume stamina; casts need rate limits. |
| P1 | Hatch quests: walk, kill, and playtime | Planned | Anti-teleport/farm checks and idempotent one-time claims. |
| P1 | Offline hatch completion timestamps | Decision pending | Must define migration from legacy remaining-time state. |
| P1 | `PetRenderer` port and ModelEngine R4 adapter | Planned | Optional class loading, exact-version pin, lifecycle cleanup, display fallback. |
| P1 | Central pet scheduler with LOD | Planned | Target load budget must be measured with 100 active pets. |
| P1 | SQLite repository and mailbox rewards | Planned | Atomic hatch completion, crash tests, backup/export, full-inventory delivery. |
| P2 | PlaceholderAPI and external quest adapters | Planned | Core must not require either plugin; event ownership must be explicit. |
| P2 | English/Vietnamese localization packs | Planned | Preserve MiniMessage placeholders and encoding safety. |
| P2 | Collection codex and discovery | Planned | Hidden-entry policy and migration for already owned pets. |
| P2 | Traits/affixes and stat budgets | Planned | Prevent unbounded combinations and pay-to-win defaults. |
| P2 | Bond/friendship progression | Planned | Avoid AFK farming; explain decay or permanence clearly. |
| P2 | Pet teams, presets, and role synergy | Planned | One-active-pet contract and GUI migration must be resolved. |
| P2 | Duplicate shards or configurable duplicate policy | Decision pending | Economy choice must be explicit and never silently destroy rewards. |
| P3 | Breeding/fusion | Future | Requires anti-dupe rules, lineage schema, and economy balancing. |
| P3 | Expeditions/offline missions | Future | Offline time, clock changes, and reward idempotency. |
| P3 | Cosmetic skins/chroma and showcase housing | Future | Asset licensing, renderer compatibility, and cleanup. |
| P3 | Trading/auction/soulbound policy | Future | Ownership transactions and rollback are high risk. |
| P3 | Seasonal events, boss affinity, achievements, and titles | Future | Content versioning and non-destructive retirement. |
| P3 | Admin balance simulator and telemetry export | Future | Metrics privacy and reproducible simulations. |
| P3 | Cross-server storage | Future | Distributed ownership/locking; not a SQLite extension flag. |

## ModelEngine milestone

ModelEngine support should ship only after the renderer contract exists:

1. `PaperDisplayRenderer` remains the built-in fallback.
2. `ModelEngineRenderer` lives in an optional adapter package/module.
3. `auto` selects ModelEngine only when the plugin is enabled and the model exists.
4. Recall, logout, death, world change, reload, and disable remove every visual/runtime object.
5. Exact R4 release is compiled and smoke-tested; public 26.x support is not assumed.

## Release gates

- All starter YAML and migration fixtures parse.
- Clean boot works with no optional dependencies.
- Every advertised optional-plugin permutation passes.
- Player persistence survives failure injection and restart.
- Legacy/new PDC and MMOItems IDs work during the documented migration window.
- Compatibility page lists exact Paper/Java/vendor builds and test date.
- No roadmap-only feature is presented as shipped documentation.
