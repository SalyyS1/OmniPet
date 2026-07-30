# Integrations

OmniPet's core gameplay does not require another plugin. Integrations are optional capabilities and must be safe to omit. Pin and test exact vendor versions; snapshot artifact availability is not a support guarantee.

## Capability matrix

| Capability | Plugin | Current status | Missing-plugin behavior |
| --- | --- | --- | --- |
| Stat modifiers on the owner | MythicLib | Implemented through `mythiclibBuffs` | Do not put the component in a pet that must load without MythicLib. |
| Skill expression | MythicLib | Implemented through `mythiclib.cast(player, skillId)` | Expression provider is unavailable. Avoid calling it in shared/default configs. |
| Pet food/evolver/egg/hatcher item stats | MMOItems | Implemented | Standalone `items.yml` remains available. |
| MMOItems expression factory | MMOItems | Implemented through `mmoitems(type, id)` | Provider is unavailable. |
| Direct MythicMobs casting | MythicMobs | Not implemented as a direct adapter | No change to core. |
| 3D model rendering | ModelEngine | Roadmap adapter boundary only | Built-in Paper display renderer remains active. |

The Paper descriptor declares MythicLib and MMOItems as optional server dependencies. OmniPet initializes each hook only when the corresponding plugin is present.

## MythicLib

### Passive stat example

```yaml
mythiclibBuffs:
  - stat: ATTACK_DAMAGE
    type: FLAT
    value: 2 + leveling.level * 0.25
  - stat: MAX_HEALTH
    type: RELATIVE
    value: 0.01 * leveling.evolution
```

Use stat IDs and modifier types supported by the exact MythicLib build on the server. Invalid IDs or API changes are vendor-contract failures; test summon, stat update, recall, logout, reload, and disable so no modifier is left behind.

### Skill expression example

```yaml
trigger:
  - type: interact
    cooldown: 240
    precondition: stamina.value >= 20
    lore:
      - "<!i><gray>Arc Pulse <yellow><trigger_cooldown>"
    script:
      - if: mythiclib.cast(player, "PET_ARC_PULSE")
        onTrue: stamina.take(20)
```

The expression calls MythicLib's skill registry, not a direct MythicMobs adapter. Keep casts on the server thread and verify missing-skill behavior before production use.

## MMOItems

OmniPet registers the following current stat IDs:

| Stat ID | Value | Purpose |
| --- | --- | --- |
| `OMNIPET_PET_FOOD` | number | Stamina restored when used on the active pet. |
| `OMNIPET_PET_EVOLVER` | boolean | Marks an item as a pet evolver. |
| `OMNIPET_EGG` | string | Egg ID from `eggs.yml`. |
| `OMNIPET_EGG_HATCHER` | number | Hatch-time reduction in seconds. |

For migration, OmniPet also registers and reads legacy `PASSIVEPET_*` versions of those four IDs. New templates should use `OMNIPET_*`; keep legacy templates until all issued items have been retired or converted.

The optional expression factory creates MMOItems instances:

```text
mmoitems("MISC", "PET_REWARD_TOKEN")
```

Validate the type and item ID. A missing template should not be used in a high-frequency trigger.

## ModelEngine boundary

ModelEngine is not declared in `paper-plugin.yml` and no ModelEngine classes are compiled into the current runtime. Planned integration should follow this boundary:

```text
Pet gameplay/state
        |
        v
PetRendererPort
   |-- PaperDisplayRenderer (built in)
   |-- ModelEngineRenderer (optional future adapter)
   `-- auto selection with fallback
```

The future adapter must:

- load only after Paper confirms ModelEngine is enabled;
- keep ModelEngine classes out of core/API class loading;
- create and remove models on the server thread;
- clean up on recall, logout, death, world change, reload, and disable;
- fall back to the Paper display renderer when a model or plugin is unavailable;
- pin and smoke-test the exact ModelEngine release.

Do not add `display.provider`, `model-id`, or similar fields to live pet YAML yet; the current codec accepts only `display.texture`.

## Version hazards

- MythicLib and MMOItems coordinates observed in research are snapshots and may change without semver stability.
- Vendor APIs can be present in Maven while not supporting the selected Paper/Minecraft line.
- Public MythicMobs/ModelEngine information inspected for this release did not establish 26.x support.
- Direct third-party references in always-loaded classes can cause linkage failure before a presence check. Keep adapters isolated.
- All entity, inventory, stat, and skill mutations belong on the Paper server thread.

## Release test permutations

At minimum, boot and exercise OmniPet with:

1. Neither optional plugin.
2. MythicLib only.
3. MythicLib and MMOItems.
4. Every exact vendor version advertised by the release.
5. Missing/invalid stat IDs, skill IDs, MMOItems IDs, and legacy IDs.

