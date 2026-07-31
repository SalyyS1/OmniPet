# Getting started

This guide covers the verified Gradle foundation, Admin Pet Studio, player vault, provider-backed active-slot purchases, and dependency-neutral incubation/catalog/escrow core. Live Paper/provider certification and the Paper hatch gameplay bridge remain pending. Live renderers, pet skills/buffs, and progression remain later phases.

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

The 2026-07-31 clean Phase 3 landing build passed 65 suites/239 tests: 157 core and 82 Paper, with zero failures, errors, or skips.

## Find the artifact

The root build copies one installable JAR to:

```text
build/release/OmniPet-3.0.0-SNAPSHOT.jar
```

The producing module also writes `omnipet-paper/build/libs/OmniPet-3.0.0-SNAPSHOT.jar`. The descriptor author is `SalyVn`.

Verified release metrics: 930,379 bytes; SHA-256 `D2F14E803BF5FCCB1D6B1FCEB22130D926C76BD2AB4BCC006E5D3C3CD0EF0109`; 611 entries, 539 classes, one `paper-plugin.yml`, and zero forbidden bundled entries.

## Foundation, vault, and Studio server check

1. Stop the test server.
2. Back up any existing `plugins/PassivePet/` and `plugins/OmniPet/` directories.
3. Put the release JAR in `plugins/`.
4. Start Paper with the Java runtime required by that exact server build.
5. Confirm the log contains `OmniPet enabled with Pet Studio and <count> definitions.` or a clear fail-closed initialization error.
6. Run `/pet` or `/pets` to open page 1 of the player vault; use `/pet 2` for another page when enough pets exist. Use `/pet slot` to inspect the next configured active-slot upgrade. Staff with `omnipet.admin.managepet` can run `/pet admin browse` to open the D/C/B/A/S Studio.

The player command requires `omnipet.general`, which defaults to `true`; Studio also requires `omnipet.admin.managepet`. Vault clicks change persisted desired-active intent only. Slot purchases are implemented but still require live certification against the exact Vault/PlayerPoints/LuckPerms builds. Core hatch outcomes and state transitions exist, but egg items/PDC, online checkpoints, recovery execution, hatch commands/GUI, live claim orchestration, and visible pet runtime behavior are not shipped yet.

## Runtime data

The bootstrap initializes these paths under `plugins/OmniPet/`:

```text
pets/                         # versioned pet definition YAML
data/players/                 # versioned player state repository
migration/legacy-eggs-v1.yml  # created only when legacy eggs.yml is present
config.yml                    # vault and active-slot limits
```

OmniPet does not copy the old PassivePet folder or expose a complete pet lifecycle. Legacy two-key slot config is migrated in place with a backup; player and definition migration still belongs on a staging copy. Follow [Migration](migration.md).

The canonical core egg catalog is `plugins/OmniPet/eggs/*.yml`, one schema 1 file per egg. The current Paper bootstrap does not create or consume that catalog for gameplay, so adding a file alone does not enable hatching.

## Compatibility scope

The build probes Paper APIs `1.21-R0.1-SNAPSHOT`, `1.21.11-R0.1-SNAPSHOT`, `26.1.1.build.29-alpha`, `26.1.2.build.74-stable`, and `26.2.build.87-stable`. The 26.x jobs use a Java 25 compiler while still producing Java 21 bytecode. These are compile checks only; no live Paper smoke test is part of this evidence.

## Next reading

- [Developer guide](developer-guide.md) for module and repository contracts.
- [Compatibility](compatibility.md) before testing a Paper build.
- [Roadmap](roadmap.md) for features not yet shipped.
