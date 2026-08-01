# Roadmap

The Gradle/persistence foundation, Admin Pet Studio slice, Phase 4 storage/economy code slice, Phase 3 dependency-neutral incubation core, and Paper paid-start/recovery coordinator are implemented and verified by automated tests. The execution plan remains in progress until later slices add hatch commands/GUI, claim presentation, live pet entities, and gameplay evidence. Later phases must not be described as shipped until their code, tests, exact Paper matrix, migration fixtures, and live smoke evidence exist.

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

## Deferred phases

| Capability | Status | Boundary/acceptance note |
| --- | --- | --- |
| Admin Pet Studio GUI and Studio Save | Shipped Phase 2 slice | D/C/B/A/S browser, dynamic MythicLib stat picker/manual fallback, draft editor, archive, exact-ID hard delete with reference blocking, clone-only authoring, guarded inventory/chat input, and shared atomic generation transaction. Full reference migration and live server UX smoke remain gates. |
| Incubation/hatching and rarity rolls | Core plus Paper start/recovery orchestration shipped; player-facing flow deferred | Deterministic outcomes, schema 4 state, catalog/journal binding, PDC identity codec, guarded inventory removal/refund seams, paid escrow saga, monotonic online scheduler, join recovery, idempotence, migration, cached egg references, and atomic claim exist. Hatch commands/GUI, live claim presentation, crash-injection proof, and live server certification remain gates. |
| Canonical multi-pet active intent and vault admission | Shipped Phase 4 slice | Core schema/storage invariants and Paper player vault are present; live renderer reconciliation belongs to Phase 5. |
| Economy-backed slot purchases | Shipped Phase 4 code slice | Vault/PlayerPoints choices, LuckPerms policy, journal discovery, and audited reconciliation are implemented. Exact provider boot/disable and live inventory smoke remain release gates. |
| Paper/ModelEngine renderers and smooth movement | Deferred to Phase 5 | ModelEngine class loading, cleanup, fallback, and exact-version tests required. |
| MythicMobs skills/interactions/riding | Deferred to Phase 6 | No direct MythicMobs adapter is shipped in the current JAR. |
| Progression, cultivation, release, and player management | Deferred to Phase 7 | Requires the earlier transactions and runtime handles. |
| Integration QA and release certification | Deferred to Phase 8 | Includes exact vendor/server smoke matrix and release-grade examples. |
| GitHub Pages | Deployed from `main` | The public site is live at `https://salyys1.github.io/OmniPet/`; current branch changes publish only after merge and a successful workflow. This is not runtime support certification. |
| GitHub wiki | Not initialized | Wiki is enabled, but no initial wiki repository/page exists; repository Markdown remains authoritative. |

## Non-claims

The current JAR ships the definition Studio, player vault/active-intent slice, slot purchase/recovery code, dependency-neutral incubation/catalog/escrow core, and the lifecycle-bound Paper paid-start/recovery coordinator with monotonic online checkpoints. It does not ship hatch GUI/commands, live claim presentation, live multi-pet renderer, MythicMobs skill execution, ModelEngine adapter, or full gameplay API. Provider and Paper runtime compatibility remain unclaimed until live smoke evidence exists.

## Release gates

Every roadmap feature needs focused unit tests, migration/rollback coverage, exact Paper compatibility evidence, clean boot without optional plugins where applicable, and a documented fallback/fail-closed path. Update the relevant docs only after those gates pass.
