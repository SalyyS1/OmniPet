# Developer guide

OmniPet is a Java 21 Paper plugin built with Gradle Kotlin DSL. The current code exposes an API interface, component codecs, item identifiers, and expression providers. Treat the 3.0 development API as evolving until a versioned API artifact is published.

## Build

```bash
# Windows
gradlew.bat clean test jar

# Linux/macOS
./gradlew clean test jar
```

The build uses:

- group `io.github.salyvn`;
- Java 21 toolchain and `--release 21`;
- UTF-8 Java/resource processing;
- compile-only Paper, MythicLib, MMOItems, and DataFixerUpper APIs;
- TinyExpr bundled into the plugin JAR;
- artifact base name `OmniPet`.

Do not shade Paper or optional server plugins. Reproducible public releases should pin/verify snapshot and JitPack inputs.

## Addon dependency

Until a repository coordinate is published, an addon can compile against a local OmniPet JAR:

```kotlin
dependencies {
    compileOnly(files("libs/OmniPet-3.0.0-SNAPSHOT.jar"))
}
```

Declare OmniPet in the addon's Paper descriptor:

```yaml
dependencies:
  server:
    OmniPet:
      load: BEFORE
      required: true
      join-classpath: true
```

## Obtain the API

Avoid depending on the implementation class when the plugin interface is enough:

```java
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.PetPlayer;

Plugin candidate = Bukkit.getPluginManager().getPlugin("OmniPet");
if (!(candidate instanceof OmniPet omniPet) || !candidate.isEnabled()) {
    return;
}

PetPlayer profile = omniPet.player(player);
if (profile != null) {
    int owned = profile.pets().size();
}
```

Resolve the plugin during your enable lifecycle, not from a static initializer. Player profiles can be absent before OmniPet has loaded the player.

## Public surfaces

`OmniPet` currently exposes:

- component codec registry: `components()` and `registerComponent(...)`;
- pet and egg registries: `pets()` and `eggs()`;
- item identification: `identifyItem(...)` and `registerItemIdentifier(...)`;
- expression scope registration: `registerProvider(...)`;
- player lookup/load/save methods;
- reload entry point.

Several API types expose Mojang Codec, Guava BiMap, Paper/Bukkit, and TinyExpr classes. Addons must treat those libraries as part of the current compile contract. A future dependency-neutral API may change this surface.

## Safe read example

```java
var eggType = omniPet.eggs().get("common");
var petType = omniPet.pets().get("example_pet");

if (eggType == null || petType == null) {
    getLogger().warning("OmniPet starter IDs are unavailable");
}
```

Do not cache registry objects across `/pets reload` unless your addon listens for and handles replacement. Prefer stable string IDs at your boundary.

## Extension guidance

- Keep third-party classes in adapter packages loaded only after plugin-presence checks.
- Never call Bukkit entity, inventory, MythicLib, MMOItems, or future ModelEngine APIs asynchronously.
- Register components/providers during plugin load before pet definitions decode.
- Validate IDs and numeric expression results before runtime use.
- Make cleanup idempotent across recall, logout, reload, and disable.
- Do not normalize user-defined pet, egg, food, or hatcher IDs.
- Do not write to player YAML while OmniPet is active without a coordinated repository API.

## ModelEngine adapter contract

There is no current ModelEngine implementation. A future adapter should implement a renderer port owned by the runtime layer, not expose ModelEngine types from the public API/core. It must support capability detection, main-thread creation/removal, lifecycle cleanup, and fallback to Paper display entities.

## Testing

Pure tests should cover duration parsing, pagination, codec round trips, component defaults, expressions, state bounds, and legacy/new item identifiers. Integration tests should cover clean boot, optional-plugin permutations, commands, GUI, persistence failure, migration, and the exact Paper matrix.

For release validation, inspect the JAR:

- contains `paper-plugin.yml` and all example resources;
- contains TinyExpr runtime classes;
- excludes Paper, MythicLib, MMOItems, MythicMobs, and ModelEngine classes;
- reports OmniPet/SalyVn branding and the Gradle project version.

## Reporting API issues

Include a minimal addon, compile dependency declaration, OmniPet commit/version, exact Paper build, Java version, and stack trace. Do not attach production player files or private server artifacts.

