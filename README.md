# OmniPet

OmniPet is a Paper plugin rewrite. This checkout is the verified Phase 1 foundation: a Gradle-only build, a two-module boundary, versioned YAML/domain contracts, legacy migration seams, and a minimal Paper bootstrap. It is not a complete pet-gameplay release.

## Phase 1 shipped

- Gradle Wrapper 9.1.0 is the only supported build authority. The clean verification command is `gradlew.bat clean build` on Windows or `./gradlew clean build` on Linux/macOS.
- `omnipet-core` contains dependency-neutral domain, YAML codecs, migration readers, atomic storage, path checks, and repository/registry seams. `omnipet-paper` contains the Paper bootstrap and produces the installable distribution JAR.
- Java 21 is the source and release baseline. Resources and Java compilation use UTF-8.
- The descriptor is branded `OmniPet`, authored by `SalyVn`, and loads `io.github.salyvn.omnipet.paper.OmniPetPlugin`.
- Paper registers `/pet` with `/pets` as its alias. Both names currently execute the foundation command and report that pet features are being built in a later phase.
- `omnipet.general` defaults to `true`; `omnipet.*` defaults to `op`; declared `omnipet.admin.*` nodes default to `false`. Admin command branches are descriptor contracts only and are not shipped gameplay commands yet.
- Player and pet definition envelopes use schema version 2. The core preserves raw unknown nodes, stable IDs, legacy state hints, and recoverable orphan data instead of silently replacing a profile.
- Atomic writes create `.bak` snapshots when replacing an existing file. Invalid player files are quarantined; a quarantined profile fails closed until an operator performs explicit recovery.
- Legacy egg definitions are validated and written to `migration/legacy-eggs-v1.yml` with a semantic SHA-256 journal. The source `eggs.yml` is never overwritten by that journal.

## Deferred roadmap

The following are not claims about the current JAR: Studio GUI, incubation/hatching gameplay, multi-pet slots and vault/economy, live renderers, MythicMobs skills, ModelEngine, economy providers, and GitHub Pages publication/maintenance. See [the roadmap](docs/roadmap.md) for phase ownership and release gates.

## Compatibility

Compatibility jobs are compile probes, not live-server certification:

| Paper API probe | Java toolchain | Position |
| --- | ---: | --- |
| `1.21-R0.1-SNAPSHOT`, `1.21.11-R0.1-SNAPSHOT` | 21 | Primary baseline probes |
| `26.1.1.build.29-alpha` | 25 | Preview/alpha probe |
| `26.1.2.build.74-stable`, `26.2.build.87-stable` | 25 | Experimental/forward probes |

Use the exact build matrix in [compatibility](docs/compatibility.md). Passing a probe does not certify Paper runtime behavior, vendor plugins, or future 26.x versions.

## Build and artifact

From this directory:

```text
gradlew.bat clean build
```

The single release artifact is `build/release/OmniPet-3.0.0-SNAPSHOT.jar` (copied from `omnipet-paper/build/libs/`). Maven commands, `pom.xml`, Maven wrappers, and the old `passivepet2` artifact name are not part of the supported workflow.

## Migration warning

Before any rebrand or data-folder move, stop the server and make a complete backup. Preserve player, pet, egg, component, and user-defined IDs. Do not run the old plugin and OmniPet against the same data at the same time. Read [Migration from PassivePet](docs/migration.md) for the legacy warnings, journal behavior, `.bak` handling, quarantine rules, and rollback procedure.

## Documentation

- [Getting started](docs/getting-started.md)
- [Commands and permissions](docs/commands-and-permissions.md)
- [Compatibility](docs/compatibility.md)
- [Configuration and schema (future/non-authoritative)](docs/configuration.md)
- [Developer guide](docs/developer-guide.md)
- [Migration](docs/migration.md)
- [Roadmap](docs/roadmap.md)

The remaining configuration, example, integration, and troubleshooting pages retain useful future syntax/design notes. Treat them as non-authoritative until their owning phases ship.
