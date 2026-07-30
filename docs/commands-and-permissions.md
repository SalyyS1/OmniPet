# Commands and permissions

Phase 1 registers one minimal Paper command. The old menu, item, egg, inspection, and admin command tree is not part of the authoritative runtime yet.

## Foundation command

| Command | Alias | Permission | Current behavior |
| --- | --- | --- | --- |
| `/pet` | `/pets` | `omnipet.general` | Returns the foundation status message. |

The implementation is intentionally small while the Studio and player command phases are built. It does not open a GUI, mutate player data, hatch eggs, give items, or summon pets.

## Descriptor defaults

These nodes are declared in `omnipet-paper/src/main/resources/paper-plugin.yml`:

| Node | Default | Phase 1 meaning |
| --- | --- | --- |
| `omnipet.*` | `op` | Grants `omnipet.general` and the declared admin tree. |
| `omnipet.general` | `true` | Allows `/pet` and `/pets`. |
| `omnipet.admin.*` | `false` | Reserved for later admin commands. |
| `omnipet.admin.reload` | `false` | Reserved; no reload command is registered yet. |
| `omnipet.admin.inspect` | `false` | Reserved; no inspect command is registered yet. |
| `omnipet.admin.explore` | `false` | Reserved; no explore command is registered yet. |
| `omnipet.admin.managepet` | `false` | Reserved; no pet-management command is registered yet. |
| `omnipet.admin.manageegg` | `false` | Reserved; no egg-management command is registered yet. |
| `omnipet.admin.item` | `false` | Reserved; no item command is registered yet. |

`omnipet.general` is the only permission currently checked by the foundation command. Granting a reserved admin node does not enable a hidden command.

## Migration note

Before cutover, export old permission groups and add the `omnipet.*` names deliberately. Do not assume the Phase 1 foundation command accepts legacy `passivepet.*` grants. Preserve `petstorage.slot.%s` and other old grants for later gameplay phases rather than deleting them during the build foundation migration.
