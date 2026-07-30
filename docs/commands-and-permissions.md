# Commands and permissions

The authoritative runtime registers the Gradle-built Paper command and the Admin Pet Studio. Player gameplay commands remain phased work.

## Command entry point

| Command | Alias | Permission | Current behavior |
| --- | --- | --- | --- |
| `/pet` | `/pets` | `omnipet.general` | Opens the player entry point or reports the current phased status. |
| `/pet admin browse` | `/pets admin browse` | `omnipet.general` + `omnipet.admin.managepet` | Opens the D/C/B/A/S definition browser and editor. |
| `/pet admin reload` | `/pets admin reload` | `omnipet.admin.reload` | Closes Studio sessions, stages all definitions, and swaps one registry generation. |

Studio Save and archive mutate only definition files through the shared transaction boundary. Player data, hatching, items, and summoning remain later phases.

## Descriptor defaults

These nodes are declared in `omnipet-paper/src/main/resources/paper-plugin.yml`:

| Node | Default | Phase 1 meaning |
| --- | --- | --- |
| `omnipet.*` | `op` | Grants `omnipet.general` and the declared admin tree. |
| `omnipet.general` | `true` | Allows `/pet` and `/pets`. |
| `omnipet.admin.*` | `false` | Wildcard for declared admin controls. |
| `omnipet.admin.reload` | `false` | Allows transactional definition reload. |
| `omnipet.admin.inspect` | `false` | Reserved; no inspect command is registered yet. |
| `omnipet.admin.explore` | `false` | Reserved; no explore command is registered yet. |
| `omnipet.admin.managepet` | `false` | Allows Pet Studio browse, create, edit, and archive. |
| `omnipet.admin.manageegg` | `false` | Reserved; no egg-management command is registered yet. |
| `omnipet.admin.item` | `false` | Reserved; no item command is registered yet. |

Every Studio action checks both the viewer permission and the current session/view token. A stale inventory, reload, close, timeout, or superseded session cannot save a draft.

## Migration note

Before cutover, export old permission groups and add the `omnipet.*` names deliberately. Do not assume the rewritten command accepts legacy `passivepet.*` grants. Preserve `petstorage.slot.%s` and other old grants for later gameplay phases rather than deleting them during migration.
