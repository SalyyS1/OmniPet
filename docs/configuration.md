# Configuration reference

OmniPet reads current configuration and data from `plugins/OmniPet/`. Files are UTF-8 YAML; use spaces, not tabs. This page documents only contracts implemented by the Gradle-built Phase 4 checkpoint.

## Authoritative files

| Path | Current purpose |
| --- | --- |
| `config.yml` | Vault capacity, active-slot limits, entitlement policy, and provider prices. |
| `pets/*.yml` | Schema 2 definition metadata edited by Admin Pet Studio. |
| `data/players/*.yml` | Schema 3 owned-pet, capacity, entitlement, and desired-active state. |
| `data/purchases/*.yml` | Schema 2 slot-purchase recovery journal. |
| `migration/legacy-eggs-v1.yml` | Validation/hash journal created from a legacy `eggs.yml`; not a hatching runtime. |

Legacy `eggs.yml`, `items.yml`, `gui.yml`, `lang.yml`, hatching components, triggers, expressions, and item recipes are not current gameplay configuration contracts. Their runtime systems and commands have not shipped.

## Player storage schema 3

```yaml
schemaVersion: 3
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

Current required fields are `classification.tier`, `icon.head.source`, `icon.head.value`, and `display.provider`. Tier is one of `D`, `C`, `B`, `A`, or `S`. Supported Studio icon sources are `TEXTURE_URL`, `BASE64`, and capability-gated `HEAD_CATALOG`.

`display.provider` accepts `HEAD` or `MODELENGINE`; `MODELENGINE` requires a non-blank `display.model`. These are authoring/persistence values only. No HEAD, Paper display-entity, or ModelEngine live renderer ships in this checkpoint.

Admin Pet Studio can also persist bounded `stats`, `rarity.bands`, `progression`, `skills`, `behavior`, and `release` metadata. The editor validates these fields and preserves unknown raw nodes, but no hatching, progression, skill execution, release gameplay, or owner-stat application consumes them yet.

## Validation checklist

- YAML parses with spaces and consistent indentation.
- Definition filename/`definitionId` values are ASCII-safe and case-fold unique.
- Required schema 2 maps and fields are present.
- Numeric stat/range values are finite and ordered.
- Slot keys such as `"2"` are quoted.
- PlayerPoints prices are integers; Vault prices round-trip safely through the provider boundary.
- Entitlement mode and precedence are consistent.
- `/pets admin reload` stages and validates configuration/definitions before activation.

See [Commands and permissions](commands-and-permissions.md), [Migration](migration.md), and [Integrations](integrations.md).
