# OmniPet operator manual

OmniPet is a configurable Paper pet plugin maintained by SalyVn. This file is copied into `plugins/OmniPet/MANUAL.md` on a new install. The public documentation is available at <https://github.com/SalyyS1/OmniPet/tree/main/docs>.

## Compatibility

| Paper line | Java | Project position |
| --- | ---: | --- |
| 1.21.x | 21 | Primary line; certify the exact Paper build you use. |
| 26.1.1 | 25 | Preview pin: `26.1.1.build.29-alpha` passed the core compile probe. |
| 26.1.2+ / 26.2 | 25 | Experimental; `26.1.2.build.74-stable` passed the core compile probe. |

The default build compiles against Paper API `1.21-R0.1-SNAPSHOT` and probes `1.21.11-R0.1-SNAPSHOT` in CI. MythicLib and MMOItems are optional. ModelEngine rendering is roadmap work; the current renderer uses Paper display entities.

## First start

1. Put `OmniPet-<version>.jar` in the server `plugins/` directory.
2. Start Paper once, then stop it.
3. Review every generated YAML file before opening the server.
4. Grant at least `petstorage.slot.1` to players.
5. Start the server and test an egg on a staging account.

Generated layout:

```text
plugins/OmniPet/
  config.yml
  eggs.yml
  gui.yml
  items.yml
  lang.yml
  pets/
  data/players/
```

Do not publish `data/players/` or attach it to public bug reports.

## Commands

Both `/pets` and `/pet` use the same command tree.

```text
/pets [page]
/pets reload
/pets inspect <players>
/pets explore <player> [path]
/pets pet give <pet> [players]
/pets pet take <slot> [player]
/pets egg set <egg> [players]
/pets egg set <egg> <players> <duration>
/pets egg clear [players]
/pets item food <food> [players]
/pets item hatcher <hatcher> [players]
/pets item evolver [players]
/pets item egg <egg> [players]
```

Page arguments are one-based; pet-list slot arguments are zero-based. If a target is omitted, the executor must be a player.

## Permissions

```text
omnipet.*
omnipet.general
omnipet.admin.*
omnipet.admin.reload
omnipet.admin.inspect
omnipet.admin.explore
omnipet.admin.managepet
omnipet.admin.manageegg
omnipet.admin.item
```

All command branches pass through `omnipet.general`. Storage slots use the separate template in `config.yml`:

```yaml
globalMaxSlots: 1000
slotPermission: petstorage.slot.%s
```

`%s` becomes the 1-based slot number. Slot permissions must be consecutive.

## Eggs

Each top-level key in `eggs.yml` is a stable egg ID:

```yaml
common:
  name: "<white>Common Egg"
  duration: 1h
  rarity: 1
  pets:
    - example_pet
    - nahara
```

- Use positive durations with `w`, `d`, `h`, `m`, and `s`.
- Every pet ID must match a filename in `pets/`.
- The current pool is uniform; weighted pools are not part of this schema.
- Rarity is copied into a hatched pet's `hatching.rarity` state.

## Pet files

The filename is the pet ID. Keep it stable after players own the pet.

```yaml
general:
  name: "<gold>Trailblazer"
  texture: "https://textures.minecraft.net/texture/..."
  description:
    - "<!i><gray>A steady companion."

display:
  texture: "https://textures.minecraft.net/texture/..."

hatching:
  defaultRarity: 0

leveling:
  maxLevel: 50
  maxExp: 100 + leveling.level * 25
  maxEvolution: 5

stamina:
  maxStamina: 120 + leveling.level * 4
```

### Trigger example

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
      - "<!i><gray>Scavenger <trigger_progressbar:18:'|'> <yellow><trigger_cooldown>"
    script:
      - stamina.take(5)
      - player.giveItem(items.FLINT)
```

Cooldowns are positive ticks when present, and `interval` triggers require one. Common trigger IDs are `walk`, `interval`, `interact`, `punch`, `takingDamage`, and `release`.

## Expressions

Expressions support literals, arithmetic, comparison, property access, calls, indexing, and ternary selection.

```text
100 + leveling.level * 25
math.max(10, leveling.level * 2)
stamina.tryTaking(5)
player.giveItem(items.EMERALD.withAmount(2))
```

Common namespaces:

- `math`: numeric helpers and `math.pi`.
- `items`: vanilla item factory with `withAmount`, `withName`, and `withLore`.
- `player`: player actions such as `giveItem` inside triggers.
- `print`: log a diagnostic expression result.
- `mmoitems`: optional MMOItems factory.
- `mythiclib`: optional MythicLib skill provider.

Expressions execute on the server thread. Keep them short and validate every ID.

## Standalone items

`items.yml` defines:

- `foods.<id>` with a numeric `stamina` value;
- `evolver`;
- `egg` template using `<egg_name>`;
- `hatchers.<id>` with a reduction `duration`.

OmniPet identifies items through persistent data, not lore. New items use the `omnipet` namespace; legacy `passivepet:*` keys are read for migration compatibility.

## Optional MythicLib

Only use this component when MythicLib is installed:

```yaml
mythiclibBuffs:
  - stat: ATTACK_DAMAGE
    type: FLAT
    value: 2 + leveling.level * 0.25
```

Optional skill call:

```yaml
trigger:
  - type: interact
    cooldown: 240
    precondition: stamina.value >= 20
    script:
      - if: mythiclib.cast(player, "PET_ARC_PULSE")
        onTrue: stamina.take(20)
```

Pin and smoke-test the exact MythicLib build. A server without MythicLib does not provide these component/expression IDs.

## Optional MMOItems

Current MMOItems stat IDs:

```text
OMNIPET_PET_FOOD
OMNIPET_PET_EVOLVER
OMNIPET_EGG
OMNIPET_EGG_HATCHER
```

Legacy `PASSIVEPET_*` versions are also read during migration. New templates should use the OmniPet IDs.

The expression `mmoitems("MISC", "ITEM_ID")` creates a configured MMOItems item when the hook is available.

## GUI and language

`gui.yml` uses MiniMessage and component lore insertions:

```yaml
pet:
  name: "<pet_name>"
  lore:
    - "{{ leveling }}"
    - "{{ stamina }}"
    - "{{ trigger }}"
    - "{{ general }}"
```

Use a chest size of `27`, `36`, `45`, or `54`. `lang.yml` contains `hud`, `messages`, and component lore. Preview MiniMessage at <https://webui.advntr.dev/> and preserve placeholders while translating.

## Migration from PassivePet

1. Stop the server and back up `plugins/PassivePet/` and `plugins/OmniPet/` separately.
2. Never run both plugin JARs together.
3. Copy legacy config, `pets/`, and `data/players/` into an empty OmniPet layout on staging.
4. Preserve filenames, egg/item IDs, YAML field names, component IDs, and `petstorage.slot.%s`.
5. Add `omnipet.*` permissions; matching legacy grants remain accepted during the migration window.
6. Test legacy/new PDC items and legacy/new MMOItems stat IDs.
7. Roll back the JAR, data folder, runtime, and permission export together if validation fails.

Do not merge two non-empty player-data directories without a conflict policy.

## Troubleshooting checklist

- Paper server and correct Java runtime.
- No duplicate PassivePet JAR.
- Positive egg durations and valid pet IDs.
- ASCII-safe IDs and UTF-8 files.
- Valid Paper material names.
- Optional components used only with their plugins.
- `omnipet.general` plus the required admin permission.
- Consecutive `petstorage.slot.N` grants.
- First relevant exception captured from the log.

For public issue reports, include exact versions and minimal redacted YAML; never include player files, tokens, credentials, IP addresses, or private assets.
