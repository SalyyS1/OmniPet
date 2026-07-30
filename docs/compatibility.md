# Compatibility

Compatibility is certified by exact build tests, not by version ranges. The table records project intent and known runtime requirements; it does not turn untested versions into supported versions.

Research snapshot: 2026-07-30.

## Server and Java truth table

| Server track | Java | Build evidence | OmniPet status | Release rule |
| --- | ---: | --- | --- | --- |
| Paper 1.21.x | 21 | Core compile probes pass for `paper-api:1.21-R0.1-SNAPSHOT` and `paper-api:1.21.11-R0.1-SNAPSHOT` | Primary | Publish support only for exact Paper builds that pass boot, commands, items, GUI, hatching, summon/recall, reload, and disable tests. |
| Paper 26.1.1 | 25 | Core compile probe passes for `paper-api:26.1.1.build.29-alpha` | Preview | Pin this exact alpha build and label the artifact experimental. Never advertise `26.1.1+` as a certified range. |
| Paper 26.1.2 | 25 | Core compile probe passes for `paper-api:26.1.2.build.74-stable` | Experimental | Run a Java 25 lane and every optional integration permutation before claiming support. |
| Paper 26.2 | 25 | Core compile probe passes for `paper-api:26.2.build.87-stable` | Forward probe | Use as a compatibility guard. Passing core tests does not automatically certify vendor adapters. |

Paper's published runtime guidance maps Minecraft/Paper 1.20 through 1.21.11 to Java 21 and 26.1+ to Java 25.

## Optional integration truth table

| Integration | Required for core | Current boundary | 1.21.x position | 26.x position |
| --- | --- | --- | --- | --- |
| MythicLib | No | `mythiclibBuffs` component and `mythiclib.cast(...)` expression | Supported only with a pinned, tested vendor build | Unverified until vendor/runtime tests pass |
| MMOItems | No | Custom item stats and `mmoitems(type, id)` expression factory | Supported only with a pinned, tested vendor build | Unverified until vendor/runtime tests pass |
| MythicMobs | No | No direct OmniPet adapter in the current tree | Not a core dependency | Vendor pages inspected did not establish 26.x support |
| ModelEngine | No | Planned `PetRenderer` adapter; not implemented | Built-in display renderer remains active | No OmniPet support claim |

Observed upstream artifacts during research included MythicLib `1.7.1-SNAPSHOT`, MMOItems API `6.10.1-SNAPSHOT`, MythicMobs `5.13.0`, and ModelEngine `R4.1.1`. Artifact availability is not a compatibility guarantee. Snapshot dependencies can change without a release version change.

## Required smoke matrix

For every advertised Paper build, test:

1. Clean boot with neither optional plugin.
2. Boot with MythicLib only.
3. Boot with MythicLib and MMOItems.
4. Pet definition load, egg definition load, and malformed-file diagnostics.
5. `/pet`, `/pets`, menu page, and every admin command branch.
6. Standalone item creation and use.
7. Legacy `passivepet:*` item recognition.
8. MythicLib modifier add/update/remove and missing-stat handling.
9. MMOItems new and legacy stat recognition.
10. Hatch, summon, recall, quit/rejoin, reload, and plugin disable.
11. No linkage errors, invalid scheduler periods, or asynchronous Bukkit calls.

## Paper API hazards

- Data-component APIs used for item name, lore, profiles, and tooltip display changed within 1.21.x.
- Paper lifecycle/Brigadier command APIs are version-sensitive.
- `api-version: 1.21` does not prove that code compiled against 1.21.6 links on 1.21.0 or 1.21.4.
- The release JAR targets Java 21 and can be loaded by a Java 25 runtime, but the 26.x Paper and vendor APIs must be tested independently.
- Avoid NMS/CraftBukkit assumptions. Put version-specific behavior behind separately loadable boundaries when necessary.

## Release labels

- **Certified:** exact Paper build, Java runtime, and optional-plugin set passed the published smoke matrix.
- **Experimental:** core behavior passed, but one or more vendor or version boundaries lack release-grade evidence.
- **Untested:** no support statement; operators may experiment at their own risk and should provide complete diagnostics.

Record the test date and exact build strings in each release. Do not use `+` or `latest` in compatibility claims.

## Sources

- [Paper getting started and Java requirements](https://docs.papermc.io/paper/getting-started/)
- [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper plugin descriptor](https://docs.papermc.io/paper/dev/plugin-yml/)
- [Minecraft version-numbering announcement](https://www.minecraft.net/en-us/article/minecraft-new-version-numbering-system)
