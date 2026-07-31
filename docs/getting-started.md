# Getting started

This guide covers the verified Gradle foundation, Admin Pet Studio, player vault, and provider-backed active-slot purchase slice. Automated evidence covers versioned definitions/player state, migration seams, transaction-backed authoring, and persisted vault behavior; live Paper/provider certification remains pending. Hatching, live renderers, pet skills/buffs, and progression remain later phases.

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

The 2026-07-31 post-fix landing build passed 54 suites/203 tests: 121 core and 82 Paper.

## Find the artifact

The root build copies one installable JAR to:

```text
build/release/OmniPet-3.0.0-SNAPSHOT.jar
```

The producing module also writes `omnipet-paper/build/libs/OmniPet-3.0.0-SNAPSHOT.jar`. The descriptor author is `SalyVn`.

Verified release metrics: 831,926 bytes; SHA-256 `893C4D6C36CEBB072100B78895DB0FE9023AEA1C33D7F9BD025BE67EB3CDD970`; 557 entries, 487 classes, one `paper-plugin.yml`, and zero forbidden bundled entries.

## Foundation, vault, and Studio server check

1. Stop the test server.
2. Back up any existing `plugins/PassivePet/` and `plugins/OmniPet/` directories.
3. Put the release JAR in `plugins/`.
4. Start Paper with the Java runtime required by that exact server build.
5. Confirm the log contains `OmniPet enabled with Pet Studio and <count> definitions.` or a clear fail-closed initialization error.
6. Run `/pet` or `/pets` to open page 1 of the player vault; use `/pet 2` for another page when enough pets exist. Use `/pet slot` to inspect the next configured active-slot upgrade. Staff with `omnipet.admin.managepet` can run `/pet admin browse` to open the D/C/B/A/S Studio.

The player command requires `omnipet.general`, which defaults to `true`; Studio also requires `omnipet.admin.managepet`. Vault clicks change persisted desired-active intent only. Slot purchases are implemented but still require live certification against the exact Vault/PlayerPoints/LuckPerms builds. Hatching and visible pet runtime behavior are not shipped yet.

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

The build probes Paper APIs `1.21-R0.1-SNAPSHOT`, `1.21.11-R0.1-SNAPSHOT`, `26.1.1.build.29-alpha`, `26.1.2.build.74-stable`, and `26.2.build.87-stable`. The 26.x jobs use a Java 25 compiler while still producing Java 21 bytecode. These are compile checks only; no live Paper smoke test is part of this evidence.

## Next reading

- [Developer guide](developer-guide.md) for module and repository contracts.
- [Compatibility](compatibility.md) before testing a Paper build.
- [Roadmap](roadmap.md) for features not yet shipped.
