# Commands and permissions

The authoritative runtime registers the Gradle-built Paper command, player vault, and Admin Pet Studio. Hatching and live pet gameplay remain phased work.

## Command entry point

| Command | Alias | Permission | Current behavior |
| --- | --- | --- | --- |
| `/pet [page]` | `/pets [page]` | `omnipet.general` | Opens the paginated player vault and toggles persisted desired-active intent. |
| `/pet admin browse` | `/pets admin browse` | `omnipet.general` + `omnipet.admin.managepet` | Opens the D/C/B/A/S definition browser and editor. |
| `/pet admin reload` | `/pets admin reload` | `omnipet.admin.reload` | Stages config/definitions, swaps the live generation, then queues online-player limit reconciliation. |

Studio Save, archive, clone-only authoring, and exact-ID hard delete mutate only definition files through the shared transaction boundary. Hard delete is blocked while YAML, egg, player, or active Studio references exist. Player vault activation changes only ordered UUID intent; no renderer entity is spawned until Phase 5. Hatching, items, summoning, and slot purchase controls remain later slices.

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

Before cutover, export old permission groups and add the `omnipet.*` names deliberately. Do not assume the rewritten command accepts legacy `passivepet.*` grants. Preserve consecutive `petstorage.slot.N` grants while migrating: the current vault resolver can consume them live according to `config.yml`.
