# Getting started

This guide covers the verified Gradle foundation, Admin Pet Studio, player vault, provider-backed active-slot purchases, and the escrow-gated Paper hatch GUI/claim flow. Live Paper/provider certification, live renderers, pet skills/buffs, and progression remain later phases.

## Requirements

- JDK 21 for the normal build and release target.
- The checked-in Gradle Wrapper 9.1.0; no system Gradle or Maven workflow is required.
- Paper API compatibility should be judged by exact compile probes and live smoke tests, not a version range.

## Build from source

Run from the OmniPet directory:

```text
# Windows
gradlew.bat clean build

# Linux/macOS
./gradlew clean build
```

`clean build` runs module tests and the branding, module-boundary, Gradle-only, and distribution checks. Exact compatibility probes are separate. Do not substitute Maven commands or add a parallel Maven artifact path.

The 2026-08-02 clean JDK 21 Gradle build completed successfully with all 18 tasks. It passed 132 suites/461 tests: core 62 suites/238 tests and Paper 70 suites/223 tests, with zero failures, errors, or skips. The Gradle-only build-path scan found zero Maven entries.

## Find the artifact

The root build copies one installable JAR to:

```text
build/release/OmniPet-3.0.0-SNAPSHOT.jar
```

The producing module also writes `omnipet-paper/build/libs/OmniPet-3.0.0-SNAPSHOT.jar`. The descriptor author is `SalyVn`.

Verified release metrics: 1,589,478 bytes; SHA-256 `0DC987AED51CD28775627DE7491850AB5FF1684D5C1ACE83A33746BE19B92386`; 976 entries, 888 classes, one `paper-plugin.yml`, and zero Maven entries.

## Foundation, vault, and Studio server check

1. Stop the test server.
2. Back up any existing `plugins/PassivePet/` and `plugins/OmniPet/` directories.
3. Put the release JAR in `plugins/`.
4. Start Paper with the Java runtime required by that exact server build.
5. Confirm the log contains `OmniPet enabled with Pet Studio and <count> definitions.` or a clear fail-closed initialization error.
6. Run `/pet` or `/pets` to open the hub, then pick a tile, or jump straight in with `/pet vault` (or `/pet 2` for another page), `/pet slot`, or `/pet hatch`. `/pet help` lists every command you can use, paged and permission-filtered; Tab after `/pet ` offers the same list. Staff with `omnipet.admin.managepet` can run `/pet admin browse` to open the D/C/B/A/S Studio.

The player command requires `omnipet.general`, which defaults to `true`; Studio also requires `omnipet.admin.managepet`. Vault clicks change persisted desired-active intent only. Slot purchases and the hatch GUI are implemented but still require live certification against the exact Vault/PlayerPoints/LuckPerms/Paper builds. Hatch start is escrow-gated, countdown advances only online, and a READY result remains pending until vault capacity allows claim. Visible pet runtime behavior is not shipped yet.

## Runtime data

The bootstrap initializes these paths under `plugins/OmniPet/`:

```text
pets/                         # versioned pet definition YAML
eggs/                         # schema 1 catalog consumed by Paper hatch start
data/players/                 # versioned player state repository
data/egg-escrow/              # durable exact-hand egg escrow and recovery journal
migration/legacy-eggs-v1.yml  # created only when legacy eggs.yml is present
config.yml                    # vault and active-slot limits
```

OmniPet does not copy the old PassivePet folder or expose a complete pet lifecycle. Legacy two-key slot config is migrated in place with a backup; player and definition migration still belongs on a staging copy. Follow [Migration](migration.md).

The canonical egg catalog is `plugins/OmniPet/eggs/*.yml`, one schema 1 file per egg. Paper consumes it for hatch start and keeps the loaded snapshot for the plugin lifetime, so stop/restart OmniPet after edits; `/pet admin reload` does not refresh eggs. A valid catalog entry still needs an exact main/off-hand egg item carrying the supported PDC identity. OmniPet does not currently provide a native egg-give/distribution command.

## Compatibility scope

The build defines probes for Paper APIs `1.21-R0.1-SNAPSHOT`, `1.21.11-R0.1-SNAPSHOT`, `26.1.1.build.29-alpha`, `26.1.2.build.74-stable`, and `26.2.build.87-stable`. The 26.x jobs use a Java 25 compiler while still producing Java 21 bytecode. These are compile checks only; the shared-queue checkpoint did not rerun them or perform live Paper smoke testing.

## Next reading

- [Developer guide](developer-guide.md) for module and repository contracts.
- [Compatibility](compatibility.md) before testing a Paper build.
- [Roadmap](roadmap.md) for features not yet shipped.
