# Getting started

This guide covers the verified Gradle foundation, Admin Pet Studio, and first player-vault slice. The JAR boots, loads versioned definitions/player state, preserves migration seams, exposes transaction-backed authoring, and opens a persisted vault. Hatching, live renderers, gameplay integrations, provider purchases, and progression remain later phases.

## Requirements

- JDK 21 for the normal build and release target.
- The checked-in Gradle Wrapper; no system Gradle or Maven workflow is required.
- Paper API compatibility should be judged by exact compile probes and live smoke tests, not a version range.

## Build from source

Run from the OmniPet directory:

```text
# Windows
gradlew.bat clean build

# Linux/macOS
./gradlew clean build
```

`clean build` runs module tests and the branding, module-boundary, Gradle-only, compatibility-compile, and distribution checks. Do not substitute Maven commands or add a parallel Maven artifact path.

## Find the artifact

The root build copies one installable JAR to:

```text
build/release/OmniPet-3.0.0-SNAPSHOT.jar
```

The producing module also writes `omnipet-paper/build/libs/OmniPet-3.0.0-SNAPSHOT.jar`. The descriptor author is `SalyVn`.

## Foundation, vault, and Studio server check

1. Stop the test server.
2. Back up any existing `plugins/PassivePet/` and `plugins/OmniPet/` directories.
3. Put the release JAR in `plugins/`.
4. Start Paper with the Java runtime required by that exact server build.
5. Confirm the log contains `OmniPet foundation enabled` with Pet Studio or a clear fail-closed initialization error.
6. Run `/pet` or `/pets` to open page 1 of the player vault; use `/pet 2` for another page when enough pets exist. Staff with `omnipet.admin.managepet` can run `/pet admin browse` to open the D/C/B/A/S Studio.

The player command requires `omnipet.general`, which defaults to `true`; Studio also requires `omnipet.admin.managepet`. Vault clicks change persisted desired-active intent only. Hatching and visible pet runtime behavior are not shipped yet.

## Runtime data

The bootstrap initializes these paths under `plugins/OmniPet/`:

```text
pets/                         # versioned pet definition YAML
data/players/                 # versioned player state repository
migration/legacy-eggs-v1.yml  # created only when legacy eggs.yml is present
config.yml                    # vault and active-slot limits
```

OmniPet does not copy the old PassivePet folder or expose a complete pet lifecycle. Legacy two-key slot config is migrated in place with a backup; player and definition migration still belongs on a staging copy. Follow [Migration](migration.md).

## Compatibility scope

The build probes Paper APIs `1.21-R0.1-SNAPSHOT`, `1.21.11-R0.1-SNAPSHOT`, `26.1.1.build.29-alpha`, `26.1.2.build.74-stable`, and `26.2.build.87-stable`. The 26.x jobs use a Java 25 compiler while still producing Java 21 bytecode. These are compile checks only; no live Paper smoke test is part of Phase 1 evidence.

## Next reading

- [Developer guide](developer-guide.md) for module and repository contracts.
- [Compatibility](compatibility.md) before testing a Paper build.
- [Roadmap](roadmap.md) for features not yet shipped.
