# Configuration reference

OmniPet reads current configuration and data from `plugins/OmniPet/`. Files are UTF-8 YAML; use spaces, not tabs. This page separates Paper-wired configuration from verified dependency-neutral core contracts.

## Authoritative files

| Path | Current purpose |
| --- | --- |
| `config.yml` | Vault capacity, active-slot limits, entitlement policy, provider prices, runtime coordinator bounds, progression defaults, and cultivation item identities. |
| `pets/*.yml` | Schema 2 definition metadata edited by Admin Pet Studio. |
| `eggs/*.yml` | Schema 1 canonical egg catalog consumed by Paper hatch start and cached Studio reference checks; restart required after edits. |
| `data/players/*.yml` | Schema 4 owned-pet, capacity, entitlement, desired-active, typed incubation, progression, skill, and release-outbox state. |
| `data/purchases/*.yml` | Schema 2 slot-purchase recovery journal. |
| `data/egg-escrow/*.yml` | Durable egg-item escrow journal used by Paper exact-hand start, commit gating, bounded recovery, and refund/cancellation decisions. |
| `data/incubation-actions/*.yml` | Durable reducer/instant-hatch item redemption journal. |
| `data/cultivation-actions/*.yml` | Durable EXP candy and breakthrough item redemption journal. |
| `data/release-mailbox/*.mailbox` | Durable release reward entitlements awaiting inventory claim or reconciliation. |
| `data/audit/studio/*.yml` | One durable audit record per accepted Studio save, archive, or hard delete. |
| `migration/legacy-eggs-v1.yml` | Validation/hash journal created from a legacy `eggs.yml`; not a hatching runtime. |

Root `eggs.yml` is migration input only; it is not the canonical schema 1 catalog. Legacy `items.yml`, `gui.yml`, `lang.yml`, hatching components, triggers, expressions, and item recipes are not current gameplay configuration contracts.

## Player storage schema 4

```yaml
schemaVersion: 4
uuid: eb70fc61-28ae-4a9f-9bda-a44e4d68101c
revision: 4
pets: []
vaultCapacity: 12
activeSlotCount: 1
desiredActivePetIds: []
slotEntitlements: []
```

- `vaultCapacity` is a persisted compatibility floor; configured capacity may increase the effective limit.
- `activeSlotCount` is unlocked ownership. Disabling multi-pet use clamps runtime intent to one without deleting entitlements.
- `desiredActivePetIds` is an ordered list of owned pet UUIDs. It is intent only; no renderer entity is persisted or spawned by this checkpoint.
- `slotEntitlements` records slot, source, transaction/reference ID, and preserved provider extension data.
- A lower vault limit never deletes pets. Existing overflow is read-only until capacity is restored.
- Unknown fields are preserved. Invalid files are quarantined and fail closed rather than becoming empty profiles.
- `incubation` is optional and singular. When present, it stores the resolved definition revision/icon snapshot/extensions, candidate ID, rarity, quality, deterministic seed/algorithm, realized stats, total/remaining active time, status, and at most the bounded applied action-token history.
- Schema 1-3 migration keeps raw `currentEgg` and preserves any pre-v4 top-level `incubation` value under legacy extension data. The core refuses a new incubation while unresolved legacy egg/incubation data exists.

The typed incubation node is persisted by core services. Paper binds the canonical egg repository, PDC/item codec, guarded inventory mutation seam, durable escrow folder, online checkpoint coordinator, conservative recovery executor, and hatch GUI/claim flow. Crash-injection proof and live Paper certification remain deferred.

## Purchase journal migration

Purchase journals currently write schema 2. When a schema 1 row is read in `COMPLETED`, `ENTITLEMENT_PERSISTED`, or `ENTITLEMENT_SYNC_PENDING`, OmniPet conservatively treats it as `ENTITLEMENT_SYNC_PENDING` with external entitlement verification required.

- Financial state and provider evidence are preserved.
- No Vault or PlayerPoints withdrawal/refund is replayed by this migration.
- The operator verifies the local entitlement and configured external node, then runs `/pet admin reconcile <transaction-uuid> sync`.
- Sync verifies the matching local OmniPet entitlement before any idempotent external grant/check.
- A successful save writes schema 2 atomically; the prior schema 1 file remains as `.bak`.
- Each journal file read is capped at 16 KiB. Oversized entries fail closed and appear as unreadable issues.
- `/pet admin transactions [limit] [cursor]` returns bounded pages and prints an opaque continuation cursor when more candidates remain.
- At most 20 unreadable issues are printed per page. Additional issues use an omission count, and truncated invalid-name discovery is reported separately.

## `messages.yml`

Player-facing chat, GUI titles, and GUI lore are owned by `plugins/OmniPet/messages.yml`, which is
generated with the built-in defaults on first start. Values use
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) and are reloaded by
`/pet admin reload`. The file is deliberately lenient: an unknown key warns and is ignored, a missing
key falls back to its default, and a value with a broken tag is shown literally. Removing a key
restores its default; deleting the file regenerates it. Placeholders such as `<pet>` and `<amount>`
are inserted literally and never re-parsed, so player-supplied text cannot inject tags into another
viewer's screen. Operator and console output — transaction pages, cursors, reconcile reasons, item
delivery receipts — stays in the plugin: it is an audit trail, not UI.

## `config.yml`

```yaml
storage:
  vault:
    baseCapacity: 30
    maxCapacity: 200
    legacyPermission:
      enabled: true
      template: "petstorage.slot.%s"
      maxScan: 200
  activeSlots:
    multiPetEnabled: true
    base: 1
    max: 5
    entitlement:
      mode: OMNIPET
      precedence: OMNIPET_AUTHORITATIVE
      luckPermsPermissionTemplate: "omnipet.slot.unlocked.%s"
    unlocks:
      "2":
        permission: ""
        costs: { VAULT: 25000, PLAYER_POINTS: 50 }
      "3":
        permission: ""
        costs: { VAULT: 75000, PLAYER_POINTS: 125 }
      "4":
        permission: "omnipet.slot.purchase.4"
        costs: { VAULT: 175000, PLAYER_POINTS: 250 }
      "5":
        permission: "omnipet.slot.purchase.5"
        costs: { VAULT: 350000, PLAYER_POINTS: 500 }
```

- `baseCapacity` is the minimum configured vault size; `maxCapacity` bounds configuration and legacy permission-derived capacity.
- Legacy permission checks are consecutive and stop at the first missing `petstorage.slot.N` node. Disable the resolver explicitly when those grants are retired.
- `base` is the active-slot floor; `max` clamps purchased entitlements. Turning `multiPetEnabled` off recalls ordered overflow intent without deleting pets or entitlements.
- `unlocks.<slot>.permission` is eligibility-only. Empty text allows every player.
- `costs` accepts bounded decimal `VAULT` values and non-negative integer `PLAYER_POINTS` values. If both providers are available, the GUI asks the player to choose; OmniPet never auto-selects currency.
- `entitlement.mode` is `OMNIPET`, `LUCKPERMS`, or `HYBRID`. Non-hybrid modes require matching authoritative precedence. Hybrid precedence is `OMNIPET_AUTHORITATIVE`, `LUCKPERMS_AUTHORITATIVE`, `REQUIRE_BOTH`, or `UNION`.
- LuckPerms nodes are consecutive. The configured permission template is expanded and validated for every slot from 2 through `storage.activeSlots.max`; an unsafe or overlong generated node rejects the config before activation. A mismatch between persisted OmniPet slots and observed nodes blocks another purchase.
- Failed external propagation stays `ENTITLEMENT_SYNC_PENDING`; retry with `/pet admin reconcile <transaction-uuid> sync` after verifying the transaction.
- Unknown keys, unsafe templates, inconsistent mode/precedence pairs, inverted limits, and unsupported values fail closed before the live snapshot changes.

Old files containing `globalMaxSlots` and/or `slotPermission` are accepted once and rewritten atomically. Missing old keys use the legacy defaults (`1000` and `petstorage.slot.%s`); `globalMaxSlots: 0` becomes zero capacity with legacy scanning disabled. The original file remains `config.yml.bak`.

### Runtime, progression, and item sections

A storage-only `config.yml` still loads; these sections use documented defaults when absent.

```yaml
runtime:
  initialDelayTicks: 1
  periodTicks: 1
  maximumOwnersPerTick: 64
  maximumPetsPerOwner: 10

progression:
  maxLevel: 100
  maxStamina: 100.0
  staminaRegenPerSecond: 1.0
  defaultExperienceFormula: "100 + level * 25 + evolution * 100"
  formulaSamples: { level: 1.0, rarity: 1.0, quality: 0.5, evolution: 0.0 }
  overflowPolicy: CARRY

items:
  experienceCandy:
    material: EXPERIENCE_BOTTLE
    experience: 100.0
  breakthroughStone:
    material: NETHER_STAR
    requiredLevel: 10
    requiredEvolution: 0

integrations:
  mythicLib: "1.7.1-SNAPSHOT build 106"
  mythicMobs: "5.9.0"
  modelEngine: "R4.0.9"
```

- `runtime` bounds the single pet coordinator. `maximumOwnersPerTick` and `maximumPetsPerOwner` cap work per tick; owners are visited round-robin so a large fleet degrades update rate instead of tick time. Scheduler values are read at enable; changing them logs a warning and requires a restart.
- `progression.defaultExperienceFormula` is compiled and evaluated against `formulaSamples` before activation. A non-finite or non-positive sample rejects the config. A pet definition may override the formula; an invalid override falls back to this validated global one.
- `overflowPolicy` is `CARRY` or `DISCARD` and decides what happens to experience granted at `maxLevel`.
- `items` defines the standalone material identities for EXP candy and breakthrough stones. MMOItems identities are not supported yet.
- `integrations` records the vendor builds the linkage-safe adapters target. These values are documentation only; they do not gate loading.
- `gui` is optional and owns menu behaviour and player feedback. Deleting the whole section reproduces the behaviour OmniPet had before it existed. See below.
- Unknown root **sections** fail closed before the live snapshot changes. The one exception is inside `gui`, which is lenient on purpose.

### `gui`

```yaml
gui:
  feedback:
    enabled: true              # master switch; false silences every sound and action bar
    minIntervalMillis: 150     # floor between two sounds from the same player
    actionBar: true            # action-bar text alongside the sound; chat is unaffected
    success:  { sound: "ENTITY_EXPERIENCE_ORB_PICKUP", volume: 0.6, pitch: 1.2 }
    failure:  { sound: "BLOCK_NOTE_BLOCK_BASS",        volume: 0.6, pitch: 0.8 }
    blocked:  { sound: "BLOCK_CHEST_LOCKED",           volume: 0.6, pitch: 1.0 }
    progress: { sound: "UI_BUTTON_CLICK",              volume: 0.4, pitch: 1.0 }
  vault:
    petsPerPage: 45            # RESTART ONLY, capped at 45
  help:
    linesPerPage: 8            # RESTART ONLY, capped at 20
  studio:
    promptTimeoutSeconds: 120  # RESTART ONLY
    autoCreateEgg: true        # write eggs/<id>_egg.yml when the Studio saves a pet
```

Unlike `storage`, this section is **lenient**, following the `messages.yml` precedent: an unknown key
warns and is ignored, a bad value falls back to that key's default, and an out-of-range page size is
clamped with a warning. A typo in a display setting must never stop players using their pets.

Feedback plays **to the acting player only** — never to nearby players — and is rate-limited per
player, so holding down a menu click cannot become an audible nuisance. Sound names resolve once at
startup; a name this Paper version does not have logs a warning and silences that one category
instead of failing. Sounds are additive: every cue sits beside its existing chat message, so
`enabled: false` removes noise, never information.

`petsPerPage` is capped at 45 because the 54-slot vault layout reserves the bottom row for paging,
sort, filter, status, hub, and slot-purchase controls.

#### Which keys reload

| Setting | `/pet admin reload` |
| --- | --- |
| `gui.feedback.*` | **Live.** Settings are resolved before anything is swapped, so a config whose sound names fail to resolve leaves the previous settings active. |
| `gui.vault.petsPerPage` | **Restart only.** The vault renderer is constructed once and reads its page size then. |
| `gui.help.linesPerPage` | **Restart only.** |
| `gui.studio.promptTimeoutSeconds` | **Restart only.** The Studio's chat-input service is built at startup and reload does not rebuild it. |
| `gui.studio.autoCreateEgg` | **Live.** Read at each save, so toggling it takes effect on the next definition you save. |

Editing a restart-only key and running `/pet admin reload` is silently ineffective by design; restart
the server to apply it.

## Provider lifecycle

Provider availability is dynamic and selective:

- disabling Vault or the plugin that owns its registered economy service invalidates only `VAULT`;
- disabling PlayerPoints invalidates only `PLAYER_POINTS`;
- disabling LuckPerms invalidates only the external entitlement adapter;
- unrelated plugin disables do not evict healthy providers;
- enable/disable events coalesce to one full refresh on the next tick.

During the refresh gap, only the affected choice reports refresh-pending/unavailable. Full OmniPet shutdown invalidates all adapters and rejects or cancels provider calls.

## Pet definition schema 2

The definition ID comes from the filename and must match `definitionId` when that field is present.

```yaml
schemaVersion: 2
definitionId: trailblazer
revision: 0
classification:
  tier: D
icon:
  head:
    source: TEXTURE_URL
    value: "https://textures.minecraft.net/texture/example"
display:
  provider: HEAD
  model: null
```

Current required fields are `classification.tier`, `icon.head.source`, `icon.head.value`, and `display.provider`. Tier is one of `D`, `C`, `B`, `A`, or `S`. Studio icon sources are `TEXTURE_URL`, `BASE64`, and capability-gated `HEAD_CATALOG`. When typing the icon in the Studio chat field, the source is auto-detected: a pasted base64 payload, an `http(s)` texture URL, or a bare 64-character texture hash are all accepted without a source keyword; the legacy `<TEXTURE_URL|BASE64|HEAD_CATALOG> <value>` form still works and is matched first.

`display.provider` accepts `HEAD` or `MODELENGINE`; `MODELENGINE` requires a non-blank `display.model`. These are authoring/persistence values only. No HEAD, Paper display-entity, or ModelEngine live renderer ships in this checkpoint.

Admin Pet Studio can also persist bounded `stats`, `rarity.bands`, `progression`, `skills`, `behavior`, and `release` metadata. The editor validates these fields and preserves unknown raw nodes, but no hatching, progression, skill execution, release gameplay, or owner-stat application consumes them yet.

## Egg definition schema 1

Save one file per egg, for example `plugins/OmniPet/eggs/tier_d_egg.yml`:

```yaml
schemaVersion: 1
eggId: tier_d_egg
tier: D
baseDuration: 1h30m
candidates:
  - definitionId: ember_fox
    weight: 3
  - definitionId: stone_wolf
    weight: 1
```

- The filename ID must match `eggId`; IDs are path-safe and case-fold unique.
- `tier` is `D`, `C`, `B`, `A`, or `S`.
- `baseDuration` accepts compact/compound values such as `90m` or `1h30m`, and ISO-8601 values such as `PT1H30M`.
- Candidates are unique pet definition IDs with finite non-negative weights and a positive total weight.
- Each file is capped at 64 KiB and catalog discovery is capped at 10,000 entries.

This is the canonical verified definition contract used by the Paper hatch flow. The Paper bootstrap loads it from `plugins/OmniPet/eggs/*.yml`, binds escrow entries at `plugins/OmniPet/data/egg-escrow/*.yml`, and caches a pet-to-egg reference index used by Studio deletion checks.

Paper egg identity uses `omnipet:egg`, `omnipet:item_nonce`, and schema-1 `omnipet:item_schema`; reads also accept legacy `passivepet:egg`. Capture serializes an amount-one clone as the durable payload, fingerprints it with SHA-256, and rejects payloads over 8 KiB. Inventory capture/removal/refund requires the player to remain online and must run on Paper's main thread. Malformed or unsupported identity, duplicate nonce, material/fingerprint mismatch, or an unexpected split amount fails closed as ambiguous instead of guessing whether the paid egg is present. Escrow entries retain the core 16 KiB file cap and 10,000-file scan bound.

The live start coordinator, scheduler/checkpoints, recovery executor, commands/GUI, and claim orchestration are wired and unit/compile tested. This evidence does not certify live Paper/server behavior. Bootstrap creates and validates the escrow directory, rejecting a regular file or symbolic-link path before the plugin exposes incubation services.

Hatch start reads `eggs/*.yml` from disk each time it runs, so an egg added or edited while the server
is up is usable immediately — including the one the Pet Studio writes when you save a definition. Two
things are still snapshots taken at bootstrap: the egg count in the enable log, and the pet-to-egg
index the Studio consults before allowing a definition to be deleted. Restart to refresh those.

## Validation checklist

- YAML parses with spaces and consistent indentation.
- Definition filename/`definitionId` values are ASCII-safe and case-fold unique.
- Required schema 2 maps and fields are present.
- Egg filename/`eggId` values match, duration parses, and candidate weights have a positive total.
- Numeric stat/range values are finite and ordered.
- Slot keys such as `"2"` are quoted.
- PlayerPoints prices are integers; Vault prices round-trip safely through the provider boundary.
- Entitlement mode and precedence are consistent.
- `/pets admin reload` stages and validates configuration/definitions before activation.

See [Commands and permissions](commands-and-permissions.md), [Migration](migration.md), and [Integrations](integrations.md).
