# Roadmap

The Gradle/persistence foundation, Admin Pet Studio slice, Phase 4 storage/economy code slice, Phase 3 dependency-neutral incubation core, Paper paid-start/recovery coordinator, and player-facing hatch GUI/claim flow are implemented and verified by automated tests. The execution plan remains in progress until later slices add live pet entities and gameplay evidence. Later phases must not be described as shipped until their code, tests, exact Paper matrix, migration fixtures, and live smoke evidence exist.

## Shipped foundation

- Gradle Wrapper-only build with dependency locks, Java 21, UTF-8, branding checks, core-boundary checks, Gradle-only checks, and one release artifact.
- Two-module boundary: `omnipet-core` owns domain/schema/migration/storage seams; `omnipet-paper` owns Paper bootstrap and distribution.
- Schema 4 player, schema 2 pet-definition, and schema 1 egg-definition envelopes with deterministic legacy pet-instance IDs, vault/active intent migration, legacy egg/incubation preservation, revision checks, raw-node preservation, and finite-value validation.
- Atomic write/backup, archive, canonical path/no-follow-link checks, quarantine, and fail-closed player recovery behavior.
- Legacy egg validation and idempotent semantic-hash journal at bootstrap.
- Minimal `/pet` command with `/pets` alias and `omnipet.general` default permission.
- Schema 4 player vault with separate owned capacity and ordered active intent, live consecutive legacy permission resolution, safe overflow behavior, coalesced reads, and serialized per-player Paper I/O.
- One plugin-owned `PerPlayerTaskQueue` shared by the vault and slot-purchase controllers: FIFO accepted work per player, concurrent work across different players, and coalescing only for pending reads with matching `vault:view` or `slot:view` keys. Reconciliation, mutations, and purchases never coalesce.
- Provider-neutral economy amount, reflection-safe Vault/PlayerPoints adapters, explicit provider-choice GUI, balance display, durable purchase/refund/entitlement-sync saga, LuckPerms precedence, and audited unknown-provider reconciliation.
- Conservative schema 1 purchase-journal completion migration, local-entitlement verification, schema 2 atomic rewrite/backup, opaque cursor paging, 16 KiB entry reads, bounded issue summaries, and dependency-aware provider invalidation with coalesced next-tick refresh.
- Race-hardened shutdown: provider schedule/register/shutdown share one lifecycle lock; controllers stop intake and close relevant UIs, bridges close/cancel, then the shared queue rejects new work and drops pending coalesced reads; accepted mutations drain for up to 10 seconds; dispatch rejection cannot remove another accepted task.
- Compile probes for exact Paper 1.21.x and 26.x coordinates. These remain probes, not runtime certification.
- Deterministic `splitmix64-v1` hatch outcome resolution and start/tick/reduce/set/complete/cancel/claim transitions with bounded idempotency tokens, UUID-reuse protection, atomic capacity-safe claim, and durable `READY` state when the vault is full.
- Canonical one-file-per-egg schema 1 repository with compact/compound/ISO duration parsing, weighted candidates, 64 KiB file reads, and a 10,000-entry catalog bound.
- Exactly-once item-escrow create/compare-transition core with stable item identity, 16 KiB entries, a 10,000-file scan bound, explicit inventory observation, and fail-closed recovery directives.
- Paper bootstrap loading of canonical `plugins/OmniPet/eggs/*.yml`, durable `plugins/OmniPet/data/egg-escrow/*.yml`, a cached pet-to-egg reference index used by Studio deletion checks, and the lifecycle-bound paid-start/recovery coordinator.
- Paper item identity using `omnipet:egg`, `omnipet:item_nonce`, and `omnipet:item_schema`, with legacy `passivepet:egg` dual-read, amount-one durable payloads capped at 8 KiB, effective item max-stack handling, and online/main-thread inventory mutation. Malformed identities, duplicate nonces, material/fingerprint mismatches, and unexpected split amounts fail closed as ambiguous.
- In-repo structured hatch notifications with `DeliveryStage.STATE_PERSISTED` after saved state changes; unchanged mutations emit none and observer failures are isolated. This is a low-level state seam, not proof of escrow/payment settlement or a separately versioned addon API.

## Deferred phases

| Capability | Status | Boundary/acceptance note |
| --- | --- | --- |
| Admin Pet Studio GUI and Studio Save | Shipped Phase 2 slice | D/C/B/A/S browser, dynamic MythicLib stat picker/manual fallback, draft editor, archive, exact-ID hard delete with reference blocking, clone-only authoring, guarded inventory/chat input, shared atomic generation transaction, and one durable audit record per accepted save. An end-to-end staff traversal and real inventory-event smoke remain gates. |
| Incubation/hatching and rarity rolls | Core plus Paper hatch flow shipped; runtime certification deferred | Deterministic outcomes, schema 4 state, catalog/journal binding, PDC identity codec, guarded exact-hand inventory removal/refund, paid escrow saga, commit-time monotonic baseline, bounded start/tick/join recovery, cached egg references, 27-slot escrow-gated hatch GUI, and atomic claim exist. The 100-tick persist cadence is the documented maximum active-time rollback. Crash-injection proof and live server certification remain gates. |
| Item distribution and reducer/instant-hatch consumables | Shipped | `/pet admin item` distributes reducer, instant-hatch, EXP candy, and breakthrough items; `/pet hatch use-main\|use-off` redeems them. Each redemption is one durable journaled transaction: exact item identity, main-thread removal, then a single effect application. MMOItems identities are not supported. |
| Offline hatch administration | Shipped | `/pet admin hatch inspect\|reduce\|set\|complete\|cancel <player-uuid> ...` runs against persisted data with split inspect/manage permissions, caller action tokens, and non-coalesced per-player serialization. |
| Canonical multi-pet active intent and vault admission | Shipped Phase 4 slice | Core schema/storage invariants, Paper player vault, and Phase 5 renderer reconciliation are present. |
| Economy-backed slot purchases | Shipped Phase 4 code slice | Vault/PlayerPoints choices, LuckPerms policy, journal discovery, and audited reconciliation are implemented. Exact provider boot/disable and live inventory smoke remain release gates. |
| Paper renderers and smooth movement | Shipped Phase 5 slice | One bounded main-thread coordinator, interpolating head renderer with explicit hard-teleport reasons, partial-spawn rollback, persisted appearance fallback, and full lifecycle cleanup. Deterministic 100-pet operation counts are recorded; live TPS/entity/network measurement remains a gate. |
| ModelEngine renderer | Shipped as optional fallback-safe adapter; uncertified | Reflective adapter falls back to the head renderer and quarantines itself on reflection failure, `LinkageError`, or spawn failure. Certification against the pinned R4 build remains a gate. |
| MythicMobs skills | Shipped Phase 6 slice | Provider-neutral bindings, reservation/commit/rollback dispatch, per-binding cooldown persistence, durable pending reservations, `/pet skill`, and `/pet admin skill`. Reflective adapter fails closed and quarantines per epoch. Provider-present smoke remains a gate. |
| Entity click interaction | Shipped | Right-clicking your own rendered pet opens its management screen. The activation index is consumed through a delegating lookup that rejects stale renderer generations; the off-hand event is filtered so one click opens one screen; an unknown entity is left uncancelled. Live confirmation that a Paper `Interaction` entity delivers `PlayerInteractEntityEvent` remains a gate. |
| Riding | Deferred | No `RideService` exists; the head renderer advertises `riding=false`. Entity click shipping does not imply riding. |
| Progression, cultivation, and release | Shipped Phase 7 slice | Bounded EXP/level/stamina/breakthrough transitions with global and per-definition formulas, durable journaled cultivation item redemption, preview-and-commit release with an atomic internal outbox, idempotent mailbox delivery, and `UNKNOWN_COMMIT` external reconciliation. |
| Player management UI | Shipped navigation hub | The combined player hub is built: `/pet` opens it with Vault, Hatch, Active slots, Help, and (staff) Studio tiles, one coalesced storage read per open, and back-to-hub controls in vault, hatch, and management. Per-pet management (favorite, lock, reorder, candy, breakthrough, release) is generation- and revision-safe. Active Party, rename control, ride/skill controls, and admin target mode remain deferred. |
| Integration QA and release certification | Deferred to Phase 8 | Includes exact vendor/server smoke matrix and release-grade examples. |
| GitHub Pages | Deployed from `main` | The public site is live at `https://salyys1.github.io/OmniPet/`; current branch changes publish only after merge and a successful workflow. This is not runtime support certification. |
| GitHub wiki | Not initialized | Wiki is enabled, but no initial wiki repository/page exists; repository Markdown remains authoritative. |

## Non-claims

The current JAR does not ship riding or passive event triggers, Active Party, rename control, MMOItems cultivation identities, a live MythicMobs skill picker in the Studio, an optional head-catalog provider, admin target mode, or a separately versioned public addon API. Provider and Paper runtime compatibility remain unclaimed until live smoke evidence exists: no crash-injection, process-kill, live lifecycle, real inventory-event, live performance, or vendor certification has been run.

## Release gates

Every roadmap feature needs focused unit tests, migration/rollback coverage, exact Paper compatibility evidence, clean boot without optional plugins where applicable, and a documented fallback/fail-closed path. Update the relevant docs only after those gates pass.
