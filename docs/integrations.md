# Integrations

OmniPet's core gameplay does not require another plugin. Integrations are optional capabilities and must be safe to omit. Pin and test exact vendor versions; snapshot artifact availability is not a support guarantee.

## Capability matrix

| Capability | Plugin | Current status | Missing-plugin behavior |
| --- | --- | --- | --- |
| Studio stat catalog | MythicLib | Implemented as a reflection-safe picker with manual fallback; runtime owner buffs are deferred | The picker is unavailable and manual IDs remain supported. |
| Owner stat application | MythicLib | Deferred to the gameplay skill/buff phase | Do not advertise a live buff until that phase ships. |
| Pet food/evolver/egg/hatcher item stats | MMOItems | Deferred to the hatching/economy phases | No item integration is active in the current runtime. |
| MMOItems expression factory | MMOItems | Deferred | No expression provider is active in the current runtime. |
| Direct MythicMobs casting | MythicMobs | Not implemented as a direct adapter | No change to core. |
| 3D model rendering | ModelEngine | Roadmap adapter boundary only | Player vault remains usable; no live pet entity is rendered yet. |

The Paper descriptor declares MythicLib and MMOItems as optional server dependencies for the catalog boundary. Live buffs, items, expressions, and gameplay hooks remain phase-gated.

> The examples below are future design/reference syntax, not current configuration contracts.

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

The hatching/item phase plans the following stat IDs; this checkpoint does not register or consume them yet:

| Stat ID | Value | Purpose |
| --- | --- | --- |
| `OMNIPET_PET_FOOD` | number | Stamina restored when used on the active pet. |
| `OMNIPET_PET_EVOLVER` | boolean | Marks an item as a pet evolver. |
| `OMNIPET_EGG` | string | Egg ID from `eggs.yml`. |
| `OMNIPET_EGG_HATCHER` | number | Hatch-time reduction in seconds. |

The migration plan retains legacy `PASSIVEPET_*` versions of those four IDs. Do not convert production templates until the owning item adapter ships and is smoke-tested.

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

The schema 2 definition can persist provider-neutral `HEAD`/`MODELENGINE` authoring data, but no renderer consumes it yet. Keep a valid head icon for cards/fallback and do not advertise a model as live until Phase 5 ships its adapter.

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
