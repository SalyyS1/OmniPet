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

## Languages (`lang/`)

`gui.locale` in `config.yml` picks a language pack from `plugins/OmniPet/lang/`. The build ships:

| Locale | Text |
| --- | --- |
| `en` | The built-in English. No file — it is the plugin's own defaults, so there is no second copy to drift. |
| `vi` | Tiếng Việt, covering every key. |

Bundled packs are written into `lang/` on first start and **never overwritten afterwards**, so a
correction you make survives an upgrade — the same rule `messages.yml` and the egg catalog follow. Add
a language by dropping `lang/<name>.yml` beside them and pointing `gui.locale` at it.

Resolution, highest priority first:

1. `messages.yml` — whatever the operator wrote
2. `lang/<locale>.yml` — the pack for the configured locale
3. the built-in English defaults

So `messages.yml` always wins, which matters because it is generated on first start and many servers
already have one edited in place. A key missing from a pack falls through to the next layer rather than
failing, so a half-finished translation shows translated lines where it has them and English elsewhere.
Naming a locale nobody has translated warns and uses English instead of refusing to start.

One caveat: a few values inside translated lines come from enum names rather than the catalog — a sort
order, a reconcile decision — and those still read in English. Player-facing failure text is covered.

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
```

- `runtime` bounds the single pet coordinator. `maximumOwnersPerTick` and `maximumPetsPerOwner` cap work per tick; owners are visited round-robin so a large fleet degrades update rate instead of tick time. Scheduler values are read at enable; changing them logs a warning and requires a restart.
- `progression.defaultExperienceFormula` is compiled and evaluated against `formulaSamples` before activation. A non-finite or non-positive sample rejects the config. A pet definition may override the formula; an invalid override falls back to this validated global one. The formula text is retained beside the compiled result, so a legacy migration rewrites your formula rather than resetting it to the default.
- `overflowPolicy` is `CARRY` or `DISCARD` and decides what happens to experience granted at `maxLevel`.
- `items` defines the standalone material identities for EXP candy and breakthrough stones. MMOItems identities are not supported yet.
- Each item accepts an optional appearance block: `items.egg`, `items.hatchReducer`, `items.instantHatch`, and a nested `appearance:` under `items.experienceCandy` and `items.breakthroughStone`. Keys are `material`, `customModelData`, `glint`, `itemFlags`, and `lore`; all are optional and lenient, so a bad value falls back for that key alone with a warning. Extra lore is appended after the built-in lines, capped at 12 lines of 256 characters — the durable egg snapshot rejects a serialized stack over 8 KiB. Item names and descriptions are not here; they live in `messages.yml` under `gui.egg.*` and `gui.item.*`.
- Appearance is read when an item is **created**. A reload restyles the next item; an egg already held or mid-hatch keeps the look it was minted with, because its escrow fingerprint describes that exact stack.
- A retired `integrations` section is still accepted, so an older file keeps loading, but it is never read and is no longer written back. Reference vendor builds now live in [integrations](integrations.md).
- `gui` is optional and owns menu behaviour and player feedback. Deleting the whole section reproduces the behaviour OmniPet had before it existed. See below.
- Unknown root **sections** fail closed before the live snapshot changes. The one exception is inside `gui`, which is lenient on purpose.

### `gui.menus`

Optional per-menu restyling. Absent means every menu renders exactly as it did before the section existed.

```yaml
gui:
  menus:
    hub:
      filler: BLACK_STAINED_GLASS_PANE
      buttons:
        vault: { material: CHEST, slot: 10 }
```

- Per menu: `size` (a multiple of 9 up to 54), `filler` (the background pane material), and `buttons`.
- Per button: `material` and `slot`.
- Buttons are keyed by **name**, not slot, so a tile whose material depends on state — the hatch tile differs when ready, incubating, and idle — can be restyled without enumerating its states. Naming one material pins the button to that material in every state, which is what naming a single material means.
- Menus and their buttons: `hub` (vault, hatch, slots, help, studio); `vault` (previous, next, hub, sort, filter, status, unlockSlot); `hatch` (startMain, startOff, incubation, hub, refresh); `management` (favorite, lock, moveUp, moveDown, candy, breakthrough, release, refresh, back, hub); `slot` (payVault, payPoints, confirm, cancel, hub).
- The vault accepts materials and a filler but **not** slots or size: pet rows occupy slots 0-44 and the pagination arithmetic is derived from that shape.
- Lenient, like the rest of `gui:`. An unknown menu or button name, a size that is not rows of nine, an out-of-range slot, and an unusable material each warn and fall back for that key alone. A move onto an already-occupied slot is refused and the button keeps its built-in position, so it cannot silently vanish.
- Item names and lore are not here; they live in `messages.yml` under `gui.*`.

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
    linesPerPage: 8            # reloads live, capped at 20
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
| `gui.help.linesPerPage` | **Live.** Help paging reads the page size on each `/pet help`, so a reload applies it. |
| `gui.studio.promptTimeoutSeconds` | **Restart only.** The Studio's chat-input service is built at startup and reload does not rebuild it. |
| `gui.studio.autoCreateEgg` | **Live.** Read at each save, so toggling it takes effect on the next definition you save. |
| `gui.menus.*` | **Live.** Read each time a menu is rendered, so reopening the menu shows the change. |

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

Admin Pet Studio can also persist bounded `stats`, `rarity.bands`, `progression`, `skills`, `behavior`, and `release` metadata. The editor validates these fields and preserves unknown raw nodes. Of these, `behavior` is read at runtime and steers the pet (see below); hatching, progression, skill execution, release gameplay, and owner-stat application do not yet consume the others.

### Per-pet movement (`behavior`)

Optional. Every key falls back to the built-in value, so a definition with no `behavior` block moves on the defaults. This is the pet's own movement *shape*; the `render:` block in `config.yml` covers how any pet is drawn and is server-wide.

```yaml
behavior:
  pattern: FOLLOW        # FOLLOW, ORBIT, HOP, or HOVER
  followDistance: 1.8    # blocks behind the owner
  sideOffset: 0.7        # blocks to the owner's side; negative mirrors it
  heightOffset: 1.25     # blocks above the owner's feet
  orbitRadius: 1.5       # ORBIT only
  orbitRadiansPerSecond: 1.5708
  bobAmplitude: 0.18     # HOP and HOVER only: how far it rises
  bobRadiansPerSecond: 6.2832
  springStrength: 18     # how hard it pulls toward its target; higher is tighter
  damping: 7             # resists overshoot; too low oscillates, too high feels sluggish
  maxAcceleration: 24
  maxSpeed: 6
  dashDistance: 5        # beyond this it dashes to catch up
  dashSpeedMultiplier: 1.75
  safetySnapDistance: 24 # beyond this it stops steering and is placed instantly
  maxDeltaSeconds: 0.25  # caps the step a lagging tick may take
  scale: 1.0             # 0 < scale <= 64
```

Steering is a spring-damper: the pet accelerates toward a target derived from the owner's position and facing, then that acceleration is clamped. `dashDistance` must be smaller than `safetySnapDistance`, or the definition is refused. Pets in a group are given a deterministic phase offset from their instance ID, so several of the same pet do not bob in lockstep.

`pattern` picks how the target is built: `FOLLOW` trails the owner, `ORBIT` circles them, `HOP` adds a bouncing rise, and `HOVER` adds a smooth rise and fall.

### ModelEngine animation clips (`behavior.animations`)

Optional, and only used by pets whose `display.provider` is `MODELENGINE`. The built-in HEAD renderer has no clips to play.

```yaml
behavior:
  animations:
    idle: idle
    walk: walk
    run: run
```

The defaults above match the names Blockbench rigs conventionally use, so a typical model animates with no configuration. Set a name to an empty string to stop that gait asking for a clip, which is how a model carrying only an idle loop avoids being asked for a walk.

A clip is switched only when the gait changes, since re-issuing the playing clip every tick would restart it. Gait comes from movement: standing still is `idle`, ordinary following is `walk`, and either reaching `render.maximumVelocity` or the controller's own catch-up dash is `run`.

Naming a clip the model does not contain costs that gait its animation and nothing else — the pet still renders. The same is true if a server's ModelEngine build does not expose the animation API at all: it is bound separately from the methods the renderer cannot work without, reported once in the log, and `RendererCapabilities.animation()` then reads false.


`rarity.bands` is optional. A definition with no `rarity` node hatches against a single implicit full-range band (`standard`, quality 0-100, hatch multiplier 1.0), so a pet saved in the Studio without authoring a rarity profile is still obtainable. An explicit `rarity` node that is malformed is still rejected — only genuine absence defaults.

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

### Placing an egg to incubate

An egg can also be placed as a block and left to hatch, with a floating countdown above it. An egg definition controls what has to be around it:

```yaml
extensions:
  placement:
    requires: lava        # the preset: a full ring of 8 lava, for a fire-affinity pet
    # or name your own:
    # blocks: [ice, packed_ice]
    # count: 3
    # describe: "ice"
    # allowed: false      # this egg can only be hatched from the hand
```

- With no `placement` node an egg accepts **any one** adjacent heat source: torch, campfire, lantern, fire, lava, magma block, furnace, and the soul variants.
- `requires: lava` demands all eight surrounding blocks be lava, so "surrounded by lava" means surrounded rather than one lucky neighbour. Intended for dragons, phoenixes, and other fire-affinity pets.
- `blocks` replaces the default heat list rather than adding to it; `count` is how many are needed; `describe` is the word used when a placement is refused.
- `allowed: false` refuses placement entirely, so that egg must be hatched with `/pet hatch main`.
- A malformed `placement` node falls back to ordinary heat rather than making the egg unhatchable.
- The requirement is re-checked every second, so removing the heat pauses the countdown instead of finishing it.
- A placed egg is stored at `plugins/OmniPet/data/placed-eggs/*.yml`, one file per block. It is **not** an escrow row: escrow proves a *held* item was paid for, while a placed egg is one the player still owns. Breaking the block consumes the record and returns that exact egg, so a break/place cycle cannot duplicate it.
- Holograms are rebuilt from the records whenever the chunk loads and are never persisted, so a crash or an unload leaves nothing to clean up.
- When a placed egg finishes and its owner's vault is full, the egg stays on the ground reading ready rather than being lost.

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
