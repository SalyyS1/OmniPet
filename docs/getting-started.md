# Getting started

This guide covers a clean OmniPet install on a Paper server. If the server has ever used PassivePet, stop and follow [Migration](migration.md) first.

## 1. Choose the runtime

| Paper line | Required Java | OmniPet position |
| --- | ---: | --- |
| 1.21.x | 21 | Primary line; test the exact Paper build you deploy. |
| 26.1.1 | 25 | Preview pin: `26.1.1.build.29-alpha` passed the core compile probe. |
| 26.1.2+ / 26.2 | 25 | Experimental; `26.1.2.build.74-stable` passed the core compile probe. |

The default Gradle build compiles against Paper API `1.21-R0.1-SNAPSHOT`; the CI compatibility lane also probes `1.21.11-R0.1-SNAPSHOT`. `api-version: 1.21` is a descriptor contract, not proof that every 1.21 patch is binary-compatible.

## 2. Install the plugin

1. Stop the server.
2. Put `OmniPet-<version>.jar` in `plugins/`.
3. Install optional dependencies only if you use their features:
   - MythicLib for `mythiclibBuffs` and `mythiclib.cast(...)`.
   - MMOItems for OmniPet item stats and the `mmoitems(...)` expression provider.
4. Start the server once.
5. Confirm the log reports `OmniPet` enabled and only initializes hooks for installed plugins.

OmniPet creates:

```text
plugins/OmniPet/
  MANUAL.md
  config.yml
  eggs.yml
  gui.yml
  items.yml
  lang.yml
  pets/
    example_pet.yml
    nahara.yml
  data/
    players/
```

Generated files are copied only when the install is new. Keep them in source control or a backup system appropriate for your server; never store player files in a public repository.

## 3. Review the starter pack

At minimum, check:

- `config.yml`: global storage cap and slot permission template.
- `eggs.yml`: positive hatch durations and valid pet IDs.
- `pets/*.yml`: pet components, MiniMessage, expressions, and optional integration use.
- `gui.yml`: menu size, item materials, component lore placeholders.
- `items.yml`: standalone food, evolver, egg, and hatcher templates.
- `lang.yml`: player-facing messages and progress-bar lore.

Run `/pets reload` after an intentional configuration change. Prefer a staging server: reload errors can leave a live server with partial configuration and are not a substitute for startup validation.

## 4. Grant storage slots

The default slot template is `petstorage.slot.%s`; `%s` becomes the 1-based slot number. A player needs every slot permission in sequence.

Example with LuckPerms:

```text
/lp group default permission set petstorage.slot.1 true
/lp group vip permission set petstorage.slot.2 true
/lp group vip permission set petstorage.slot.3 true
```

All player commands require `omnipet.general`, which defaults to true. Admin subcommands require the matching `omnipet.admin.*` permission as well.

## 5. Test the first hatch

```text
/pets item egg common <player>
```

The player right-clicks the egg to begin hatching. Use a hatcher item to reduce the remaining time:

```text
/pets item hatcher elixir <player>
```

For a faster staging test, an administrator can directly set an egg and duration:

```text
/pets egg set common <player> 10s
```

After hatching, open `/pets`, left-click the pet to summon it, and interact with its display to test food, evolver, or trigger behavior.

## 6. Production checklist

- Back up the plugin data folder before each upgrade.
- Pin the exact Paper, Java, MythicLib, and MMOItems versions used for release testing.
- Confirm clean boot with no optional plugins and with each enabled integration.
- Validate summon, recall, hatch, quit/rejoin, reload, and disable behavior.
- Keep user-authored expressions bounded and review any item-granting trigger.
- Never use lore or display names as security identifiers; OmniPet items use persistent data.

## Build from source

From the OmniPet project directory:

```bash
# Windows
gradlew.bat clean test jar

# Linux/macOS
./gradlew clean test jar
```

The release JAR is written under `build/libs/`, with a copy under `build/release/` after a full build. Use Gradle commands in automation and documentation; Maven metadata in historical files is not the release workflow.
