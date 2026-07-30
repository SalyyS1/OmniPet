# OmniPet

OmniPet is a configurable Paper plugin for collectible pets, egg hatching, pet storage, leveling, stamina, triggers, and optional Mythic ecosystem integrations. It is maintained by SalyVn and keeps the original YAML component model while the runtime is being modernized.

> This repository is a development snapshot. Read the compatibility table before choosing a server build.

## What is available now

- Paper-native `/pets` and `/pet` menus with paginated storage.
- YAML pet definitions with `general`, `display`, `hatching`, `leveling`, `stamina`, and `trigger` components.
- Egg definitions with duration, rarity, and pet pools.
- Standalone food, evolver, egg, and hatcher items.
- Optional MythicLib stat/skill expressions and MMOItems item identifiers.
- Adventure MiniMessage text and the TinyExpr expression language.
- A rebranded API under `io.github.salyvn.omnipet`.

ModelEngine rendering is deliberately a roadmap item. The current built-in renderer uses Paper display entities; do not add a `ModelEngine` dependency to a production server expecting it to be used automatically.

## Compatibility

| Server track | Java | Status | Notes |
| --- | ---: | --- | --- |
| Paper 1.21.x | 21 | Primary baseline | Core compile probes pass for `1.21-R0.1-SNAPSHOT` and `1.21.11-R0.1-SNAPSHOT`; still smoke-test the exact build you deploy. |
| Paper 26.1.1 | 25 | Preview/alpha caveat | Core compile probe passes for `26.1.1.build.29-alpha`; this is an alpha pin, not a blanket `26.1.1+` certification. |
| Paper 26.1.2 and later 26.x | 25 | Experimental | Core compile probe passes for `26.1.2.build.74-stable`; optional vendor plugins have no published 26.x guarantee in this project. |

MythicLib and MMOItems are optional. OmniPet must start without either plugin; their APIs are vendor-version contracts and should be pinned and smoke-tested with the server build you publish.

See [the full compatibility matrix](docs/compatibility.md).

## Install and build

1. Install a supported Paper server and the matching Java runtime.
2. Download the `OmniPet-<version>.jar` artifact into `plugins/`.
3. Start the server once. OmniPet copies starter files into `plugins/OmniPet/`.
4. Review `config.yml`, `eggs.yml`, `gui.yml`, `items.yml`, `lang.yml`, and `pets/*.yml` before inviting players.

Build from this directory with Gradle:

```bash
# Linux/macOS
./gradlew clean test jar

# Windows PowerShell or cmd
gradlew.bat clean test jar
```

The Java 21 toolchain and UTF-8 resource encoding are configured by `build.gradle.kts`. Maven commands and the old `passivepet2` artifact name are not part of the supported workflow.

## First commands

```text
/pets
/pets 1
/pets item food petSteak
/pets item egg common
/pets item hatcher elixir
/pets item evolver
```

`/pet` is a stable alias for `/pets`. Administrators can use `/pets reload`, `/pets inspect <player>`, `/pets explore <player> [path]`, `/pets pet ...`, and `/pets egg ...`; details are in [Commands and permissions](docs/commands-and-permissions.md).

## Documentation

- [Getting started](docs/getting-started.md)
- [Configuration reference](docs/configuration.md)
- [Commands and permissions](docs/commands-and-permissions.md)
- [Integrations](docs/integrations.md)
- [Migration from PassivePet](docs/migration.md)
- [Developer guide and API](docs/developer-guide.md)
- [Copy-safe examples](docs/examples.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Roadmap](docs/roadmap.md)
- [Bundled examples](src/main/resources/example/)

The same Markdown files are suitable for a GitHub wiki. A no-secret GitHub Pages workflow publishes `docs/` as a static site.

## Migration summary

Keep a complete backup before changing the plugin folder name. Preserve pet, egg, item, component, expression, and command IDs. OmniPet writes the `omnipet` PDC namespace and reads legacy `passivepet:*` item keys; MMOItems adapters read both `OMNIPET_*` and legacy `PASSIVEPET_*` stat IDs. New permissions should use `omnipet.*`; matching legacy command grants remain accepted during migration.

The migration guide includes a rollback procedure and a list of fields that must not be renamed casually.

## Support expectations

When reporting an issue, include the OmniPet version, exact Paper build, Java version, optional plugin versions, startup log around hook detection, and a minimal redacted YAML file. Never attach player data, tokens, or private server logs containing personal information.
