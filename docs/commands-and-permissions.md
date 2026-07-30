# Commands and permissions

OmniPet registers `/pets` and `/pet` through Paper's modern command lifecycle. Both names use the same command tree and can be used with selectors, `/execute`, command blocks, and functions where Paper permits them.

## Player commands

| Command | Permission | Description |
| --- | --- | --- |
| `/pets` | `omnipet.general` | Open page 1 of the pet menu. |
| `/pets <page>` | `omnipet.general` | Open a one-based pet menu page. |
| `/pet` | `omnipet.general` | Stable alias for `/pets`. |

Example:

```text
/execute as @a[tag=pet-preview] run pets
```

## Administrative commands

Every admin command also passes through the root `omnipet.general` check. Grant both the admin node and general access if your permissions plugin overrides defaults.

| Command | Permission | Description |
| --- | --- | --- |
| `/pets reload` | `omnipet.admin.reload` | Reload configs and online player runtime state. Test changes on staging first. |
| `/pets inspect <players>` | `omnipet.admin.inspect` | Show egg and pet summaries for one or more profiles. |
| `/pets explore <player> [path]` | `omnipet.admin.explore` | Explore codec-backed player data for diagnostics. Treat output as sensitive. |
| `/pets pet give <pet> [players]` | `omnipet.admin.managepet` | Add a default instance of a configured pet type. |
| `/pets pet take <slot> [player]` | `omnipet.admin.managepet` | Remove the zero-based pet-list slot. |
| `/pets egg set <egg> [players]` | `omnipet.admin.manageegg` | Start the configured egg duration. |
| `/pets egg set <egg> <players> <duration>` | `omnipet.admin.manageegg` | Start an egg with an explicit duration such as `10m` or `2h30m`. |
| `/pets egg clear [players]` | `omnipet.admin.manageegg` | Clear the current hatch job. |
| `/pets item food <food> [players]` | `omnipet.admin.item` | Give a standalone food from `items.yml`. |
| `/pets item hatcher <hatcher> [players]` | `omnipet.admin.item` | Give a hatch-time reduction item. |
| `/pets item evolver [players]` | `omnipet.admin.item` | Give the configured evolver. |
| `/pets item egg <egg> [players]` | `omnipet.admin.item` | Give a configured egg item. |

When `[players]` is omitted, the executor must be a player and becomes the target. Console automation should always supply an explicit selector or player.

## Permission tree

| Node | Default | Children/purpose |
| --- | --- | --- |
| `omnipet.*` | op | `omnipet.general`, `omnipet.admin.*` |
| `omnipet.general` | true | Player command root |
| `omnipet.admin.*` | false | All admin nodes |
| `omnipet.admin.reload` | false | Reload |
| `omnipet.admin.inspect` | false | Summary inspection |
| `omnipet.admin.explore` | false | Codec explorer |
| `omnipet.admin.managepet` | false | Pet add/remove |
| `omnipet.admin.manageegg` | false | Egg set/clear |
| `omnipet.admin.item` | false | Give plugin items |

## Storage permissions

Storage uses the `slotPermission` template from `config.yml`, not the `omnipet.*` tree. The default is:

```yaml
slotPermission: petstorage.slot.%s
```

`%s` becomes `1`, `2`, and so on. Permissions must be consecutive: having slot 5 without slots 1-4 does not create five usable slots.

## Migration notes

The legacy `passivepet.*` nodes are accepted as fallback command checks, but are not the primary OmniPet names. During migration:

1. Add equivalent `omnipet.*` grants to groups, command blocks, menus, and automation.
2. Keep old grants temporarily, then remove them after all command users migrate.
3. Preserve `petstorage.slot.%s` unless you deliberately migrate every slot grant.
4. Audit console scripts for the stable `/pet` and `/pets` aliases; no command rename is required.

## Safety

- Back up before bulk `pet take`, `egg clear`, or reload operations.
- Avoid exposing `/pets explore` output publicly; player data can include UUIDs and progression state.
- Use selectors narrowly. `@a` item or pet grants can create a large number of mutations in one command.
- Test command syntax after changing Paper builds because the command API is version-sensitive.
