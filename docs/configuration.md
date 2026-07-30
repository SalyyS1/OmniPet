# Configuration reference

OmniPet reads YAML from `plugins/OmniPet/`. Files are UTF-8 and user-visible text uses Adventure MiniMessage. Use spaces, not tabs. Keep identifiers ASCII-safe unless a feature explicitly documents Unicode handling.

> The definition envelope and Admin Pet Studio sections below describe the current authoritative runtime. Egg, item, hatching, trigger, expression, and renderer examples are retained as future/non-authoritative design reference until their roadmap phase ships.

## Stable contracts

The following names are persistent or referenced across files. Do not rename them casually:

- Files: `config.yml`, `eggs.yml`, `gui.yml`, `items.yml`, `lang.yml`, `pets/*.yml`.
- Global keys: `globalMaxSlots`, `slotPermission`.
- Egg keys: `name`, `duration`, `rarity`, `pets`.
- Component IDs: `general`, `display`, `hatching`, `leveling`, `stamina`, `trigger`, optional `mythiclibBuffs`.
- User IDs: pet filenames, egg map keys, food map keys, and hatcher map keys.
- Expression namespaces: `math`, `player`, `items`, `print`, optional `mmoitems`, optional `mythiclib`.
- Trigger IDs: `walk`, `interval`, `interact`, `punch`, `takingDamage`, `release`.

Renaming a pet filename changes its ID and can break eggs, player saves, commands, expressions, and items.

## Admin Pet Studio

The Studio is code-owned and deliberately does not require a second GUI YAML schema. Open it with `/pet admin browse` or `/pets admin browse` after granting `omnipet.admin.managepet`.

- Tier order is fixed: `D`, `C`, `B`, `A`, `S`.
- Definition IDs are ASCII-safe and immutable while editing.
- Head icons are mandatory. Supported sources are `TEXTURE_URL`, `BASE64`, and capability-gated `HEAD_CATALOG`.
- Renderer choices are `HEAD` or `MODELENGINE`; selecting ModelEngine still keeps the head icon for cards/eggs.
- Stats can be selected from the live MythicLib registry and configured as `MODIFIER min max`. The manual fallback accepts `id MODIFIER min max`; new picker IDs use `mythiclib:<lowercase-id>` and retain the exact vendor ID for the runtime adapter.
- MMOItems can contribute owner stat handlers through MythicLib, but its item-template stat registry is intentionally not mixed into the owner-buff picker.
- Rarity uses `id qualityMin qualityMax weight hatchMultiplier`.
- Progression uses `maxLevel;formula`; skills use `provider:id|trigger|cooldown|chance|stamina|target`.
- Type/chat validation happens before a draft can be saved. Unknown raw YAML nodes are retained.
- Save, archive, and `/pets admin reload` use one optimistic, atomic registry-generation transaction. A stale revision, reference, or activation failure leaves the prior disk/live generation active.

## `config.yml`

```yaml
# Hard ceiling for the generated slot permission list.
globalMaxSlots: 1000

# %s becomes the 1-based slot number.
slotPermission: petstorage.slot.%s
```

Keep the default permission template during migration unless every group and user grant is updated at the same time.

## `eggs.yml`

Each top-level key is an egg ID.

```yaml
common:
  name: "<white>Common Egg"
  duration: 1h
  rarity: 1
  pets:
    - example_pet
    - nahara
```

- `name`: MiniMessage component used in items, HUD, and messages.
- `duration`: positive duration using `w`, `d`, `h`, `m`, and `s`, for example `1w2d3h4m5s`.
- `rarity`: numeric value copied into a hatched pet's `hatching.rarity` state.
- `pets`: pet IDs from filenames under `pets/`. The current selection is uniform; weighted pools are roadmap work.

Avoid blank/zero durations and empty pools in production.

## `pets/<id>.yml`

Each top-level key is a component. An intentionally minimal pet may be empty, but most pets need at least `general` and `display` for usable presentation.

### `general`

```yaml
general:
  name: "<gold>Trailblazer"
  texture: "https://textures.minecraft.net/texture/..."
  description:
    - "<!i><gray>A steady companion for long journeys."
```

The texture must use the Minecraft texture URL prefix to render as a player head.

### `display`

```yaml
display:
  texture: "https://textures.minecraft.net/texture/..."
```

The current renderer creates Paper display/interaction entities. ModelEngine-specific fields are not accepted by this schema yet.

### `hatching`

```yaml
hatching:
  defaultRarity: 0
```

Runtime values:

- `hatching.rarity`: rarity supplied by the egg.
- `seed`: persisted internal state used for deterministic randomization seams.

### `leveling`

```yaml
leveling:
  maxLevel: 50
  maxExp: 100 + leveling.level * 25
  maxEvolution: 5
```

Available values/methods include `leveling.exp`, `leveling.level`, `leveling.evolution`, `leveling.maxExp`, `leveling.maxLevel`, `leveling.maxEvolution`, `leveling.addExp(amount)`, `leveling.setExp(value)`, `leveling.setLevel(value)`, `leveling.setEvolution(value)`, and `leveling.evolve()`.

Keep maximum expressions finite and positive.

### `stamina`

```yaml
stamina:
  maxStamina: 120 + leveling.level * 4
```

Available values/methods include `stamina.value`, `stamina.max`, `stamina.set(value)`, `stamina.add(amount)`, `stamina.take(amount)`, and `stamina.tryTaking(amount)`.

### `trigger`

```yaml
trigger:
  - type: walk
    script:
      - if: stamina.tryTaking(trigger.walkDistance * 0.05)
        onTrue: leveling.addExp(trigger.walkDistance)

  - type: interval
    cooldown: 100
    precondition: stamina.value >= 5
    lore:
      - ""
      - "<!i><gray>Scavenger <trigger_progressbar:20:'|'> <yellow><trigger_cooldown>"
    script:
      - stamina.take(5)
      - player.giveItem(items.FLINT)
```

Cooldowns are measured in ticks (`20` ticks is approximately one second) and must evaluate to a positive value when present. `interval` triggers require a cooldown; invalid triggers are skipped instead of running every tick. A script can be one expression, a list, or an `if` block with `onTrue` and `onFalse`.

Trigger-specific values:

- `walk`: `trigger.walkDistance` in blocks.
- `takingDamage`: `trigger.damage` and `trigger.realDamage`.
- `interval`, `interact`, `punch`, `release`: no additional values.

Expressions execute on the server thread. Keep scripts short, validate every referenced item/skill ID, and avoid high-frequency work.

### Optional `mythiclibBuffs`

Use only when MythicLib is installed:

```yaml
mythiclibBuffs:
  - stat: ATTACK_DAMAGE
    type: FLAT
    value: 2 + leveling.level * 0.25
```

Do not include this component in a pet that must load on a server without MythicLib. See [Integrations](integrations.md).

## Expressions

Expressions support numeric/string literals, parentheses, arithmetic, comparison, bitwise operators, property lookup, method calls, indexing, and ternary selection.

```text
100 + leveling.level * 25
stamina.value >= 10 ? 1 : 0
player.giveItem(items.EMERALD.withAmount(1 + leveling.evolution))
math.max(10, leveling.level * 2)
```

Comparison returns truth-compatible values. Text may use single or double quotes. The `items` namespace creates vanilla items and supports `withAmount`, `withName`, and `withLore`.

## `items.yml`

Standalone items use these stable sections:

```yaml
foods:
  petSteak:
    type: COOKED_BEEF
    name: "<red>Pet Steak"
    lore:
      - "<!i><gray>Restores <yellow>50</yellow> stamina."
    stamina: 50

evolver:
  type: PAPER
  name: "<light_purple>Scroll of Ascension"

egg:
  type: EGG
  name: "<yellow><egg_name> Egg"

hatchers:
  elixir:
    type: GLASS_BOTTLE
    name: "<aqua>Time Elixir"
    duration: 3h
```

OmniPet identifies these items using persistent data, not lore. New items use the `omnipet` namespace; legacy `passivepet:*` keys are read for migration compatibility.

## `gui.yml`

Required template IDs are `border`, `emptySlot`, `lockedSlot`, `voidSlot`, `egg`, `pet`, `activePet`, `nextPage`, and `prevPage`. Each item template may use:

- `type`: Bukkit material ID.
- `name`: MiniMessage.
- `lore`: MiniMessage lines or component insertion such as `{{ leveling }}`.
- `tooltip: false`: hide the vanilla tooltip on supported Paper builds.

Use a chest size of `27`, `36`, `45`, or `54`. The default starter uses `54`.

Common placeholders include `<pet_name>`, `<egg_name>`, `<egg_hatch_duration>`, `<page>`, `<max_page>`, `{{ general }}`, `{{ leveling }}`, `{{ stamina }}`, and `{{ trigger }}`.

## `lang.yml`

`lang.yml` contains `hud`, `messages`, and component lore. It is MiniMessage, not legacy ampersand color codes. Preview formatting with [Adventure MiniMessage Web UI](https://webui.advntr.dev/).

Preserve placeholders when translating. A missing placeholder may remove useful context; an unknown custom tag can fail deserialization.

## Validation checklist

- YAML parses with spaces and consistent indentation.
- Every egg pet ID matches a pet filename exactly.
- Every duration is positive.
- Every material is a valid enum for the target Paper build.
- Every optional component/provider has its plugin installed.
- Every expression returns the type expected by its field.
- Menu size and placeholders are valid.
- A clean staging start and `/pets admin reload` complete without errors.
