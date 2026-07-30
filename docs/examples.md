# Examples

The bundled starter pack under `src/main/resources/example/` is the canonical copy-safe example. These smaller recipes demonstrate common patterns without changing the live schema.

## Balanced starter pet

```yaml
general:
  name: "<gold>Trailblazer"
  texture: "https://textures.minecraft.net/texture/dd871e28db04d3711792e0fa549e997f846ac950412ba091a606f324459d38d3"
  description:
    - "<!i><gray>A steady companion for long journeys."

display:
  texture: "https://textures.minecraft.net/texture/dd871e28db04d3711792e0fa549e997f846ac950412ba091a606f324459d38d3"

hatching:
  defaultRarity: 0

leveling:
  maxLevel: 50
  maxExp: 100 + leveling.level * 25
  maxEvolution: 5

stamina:
  maxStamina: 120 + leveling.level * 4

trigger:
  - type: walk
    script:
      - if: stamina.tryTaking(trigger.walkDistance * 0.05)
        onTrue: leveling.addExp(trigger.walkDistance)
```

Save as `pets/trailblazer.yml`; the pet ID becomes `trailblazer`.

## Egg pool

```yaml
traveler:
  name: "<green>Traveler Egg"
  duration: 45m
  rarity: 1
  pets:
    - trailblazer
    - nahara
```

The current pool is uniform. Repeating an ID to fake weights is not recommended; weighted pools belong to the versioned roadmap schema.

## Standalone food

```yaml
foods:
  berryBowl:
    type: SWEET_BERRIES
    name: "<red>Berry Bowl"
    lore:
      - "<!i><gray>Restores <yellow>25</yellow> stamina."
      - ""
      - "<!i><yellow>Use on your summoned pet."
    stamina: 25
```

Give it with:

```text
/pets item food berryBowl <player>
```

## Conditional release reward

```yaml
trigger:
  - type: release
    script:
      - player.giveItem(items.FEATHER
          .withAmount(1 + leveling.level / 10)
          .withName("<aqua>Memory Feather")
          .withLore("<!i><gray>A keepsake from a released companion."))
```

Keep reward formulas bounded. Releasing is destructive gameplay; test the exact GUI interaction and backup behavior.

## Optional MythicLib passive

```yaml
mythiclibBuffs:
  - stat: ATTACK_DAMAGE
    type: FLAT
    value: 1 + leveling.level * 0.2
```

This pet requires MythicLib to decode that optional component. Do not put it in a universal starter pack intended to boot without vendor plugins.

## Optional MythicLib skill

```yaml
trigger:
  - type: interact
    cooldown: 240
    precondition: stamina.value >= 20
    lore:
      - "<!i><gray>Arc Pulse <trigger_progressbar:20:'|'> <yellow><trigger_cooldown>"
    script:
      - if: mythiclib.cast(player, "PET_ARC_PULSE")
        onTrue: stamina.take(20)
```

The cooldown is in ticks. Confirm the skill ID and failure result against the pinned MythicLib build.

## MMOItems reward expression

```yaml
trigger:
  - type: release
    script:
      - player.giveItem(mmoitems("MISC", "PET_MEMORY_TOKEN"))
```

This requires MMOItems and a matching item template. Prefer standalone vanilla items when a config must work without MMOItems.

## What not to copy yet

The following is roadmap syntax and is not accepted by the current pet codec:

```yaml
# Not supported yet.
display:
  provider: modelengine
  model-id: ember_fox
```

Use only `display.texture` until the renderer adapter and versioned migration are implemented.

