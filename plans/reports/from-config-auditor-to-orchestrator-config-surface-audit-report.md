# OmniPet Config Surface Audit

Scope: `omnipet-paper/src/main/resources/config.yml` (108 lines) vs. code readers in `omnipet-core` + `omnipet-paper`. Read-only audit, no Gradle run.

Loaders: `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/config/OmniPetConfigLoader.java` (aggregate), `Phase4PaperConfigLoader.java` + `Phase4ActiveSlotConfigCodec.java` (strict `storage:`), `GuiConfigLoader.java` (lenient `gui:`).

## 1. Dead keys

**`integrations:` (config.yml:69-72) is fully dead.** Allowed as a root key at `OmniPetConfigLoader.java:22` but `parse()` (lines 42-56) never touches `root.get("integrations")`. The only code mentioning `mythicLib` / `mythicMobs` / `modelEngine` as config names is `encode()` at `OmniPetConfigLoader.java:80-83`, which writes hardcoded literals. So decode ignores the section and encode overwrites it. The comment at config.yml:68 ("These values are documentation only") is honest, but the values are unreachable at runtime — real MythicLib detection is reflective via plugin manager (`omnipet-paper/.../catalog/ReflectiveMythicLibStatCatalogSource.java:33`) and load order is declared in `omnipet-paper/src/main/resources/paper-plugin.yml:10-24`.

No other dead keys. Every other key resolves:

| Key | Reader |
| --- | --- |
| `storage.vault.baseCapacity` / `maxCapacity` | `Phase4PaperConfigLoader.java:64-65`; consumed `permission/PaperStorageLimitsResolver.java:28-30` |
| `storage.vault.legacyPermission.enabled` / `template` / `maxScan` | `Phase4PaperConfigLoader.java:60-62`; consumed `permission/LegacyVaultPermissionResolver.java:15-24` |
| `storage.activeSlots.multiPetEnabled` / `base` / `max` | `Phase4ActiveSlotConfigCodec.java:26,34-37`; consumed `PaperStorageLimitsResolver.java:31-37` |
| `storage.activeSlots.entitlement.mode` / `precedence` / `luckPermsPermissionTemplate` | `Phase4ActiveSlotConfigCodec.java:70-81`; consumed `PaperStorageLimitsResolver.java:49-58` |
| `storage.activeSlots.unlocks.*.permission` / `costs` | `Phase4ActiveSlotConfigCodec.java:95-104` |
| `runtime.*` (4 keys) | `OmniPetConfigLoader.java:100-103`; consumed `runtime/PaperPetRuntimeCoordinator.java:72,81,206` |
| `progression.*` (6 keys) | `OmniPetConfigLoader.java:109-126`; consumed `omnipet-core/.../progression/ProgressionService.java:27,36,42,57,94` |
| `items.experienceCandy.*` / `items.breakthroughStone.*` | `OmniPetConfigLoader.java:136-140`; consumed `management/PaperPetConsumableInventory.java:55-77` |
| `gui.feedback.*` (incl. 4 cues × sound/volume/pitch) | `GuiConfigLoader.java:91-127`; consumed `feedback/FeedbackService.java:88-96` |
| `gui.vault.petsPerPage` | `GuiConfigLoader.java:36-38`; consumed `gui/player/PlayerPetMenuRenderer.java:31,43` |
| `gui.help.linesPerPage` | `GuiConfigLoader.java:39-41`; consumed `command/CommandHelp.java:54` |
| `gui.studio.promptTimeoutSeconds` / `autoCreateEgg` | `GuiConfigLoader.java:44-48`; consumed `studio/bukkit/PetStudioController.java:111,114,406` |

## 2. Undocumented keys (code default not in shipped file)

**None.** Every `getOrDefault` default in the loaders matches a key that IS present in the shipped file, and the shipped value equals the code default in every case:

- `OmniPetConfigLoader.java:100-103` runtime defaults `1/1/64/10` == config.yml:44-47.
- `OmniPetConfigLoader.java:110,113,118,121-123` progression defaults == config.yml:51-56.
- `OmniPetConfigLoader.java:136-140` item defaults == config.yml:60-66.
- `GuiConfig.defaults()` (`GuiConfig.java:41`, `GuiConfig.java:83-91`) == config.yml:82-108.

Adjacent (not config, worth flagging): `render/PaperHeadRendererSettings.java:23-25` holds four HEAD-renderer tunables (`8.0 / 0.35 / 1.2 / 3`) with a `defaults()`-only construction path (`render/PaperHeadRenderer.java:22`) — no config surface exists for them at all. Not a documented/undocumented mismatch, just an un-exposed knob.

## 3. Encode / decode mismatches

Section coverage is complete — every section decode reads is written by encode:

- `OmniPetConfigLoader.encode()` (58-94) writes `storage`, `runtime`, `progression`, `items`, `integrations`, `gui`; decode reads `storage`, `runtime`, `progression`, `items`, `gui`. No section dropped. `gui` is explicitly serialised at line 92 with the reason in the comment at 90-91.
- `Phase4PaperConfigLoader.encode()` (71-86) writes vault{baseCapacity, maxCapacity, legacyPermission{enabled, template, maxScan}} + activeSlots — exact mirror of the decode key sets at lines 18-19, 53, 57.
- `Phase4ActiveSlotConfigCodec.encode()` (41-66) writes multiPetEnabled, base, max, entitlement{mode, precedence, luckPermsPermissionTemplate}, unlocks{permission, costs} — mirror of `parse()` (24-38) and `parseEntitlement()` (68-82).
- `GuiConfigLoader.encode()` (63-81) writes feedback{enabled, minIntervalMillis, actionBar, 4 cues}, vault.petsPerPage, help.linesPerPage, studio{promptTimeoutSeconds, autoCreateEgg} — mirror of `parse()` (27-59). Round-trip is asserted at `omnipet-paper/src/test/.../config/GuiConfigLoaderTest.java:165`.

**Two value-level (not section-level) lossy encodes:**

1. `OmniPetConfigLoader.java:69` — `progression.put("defaultExperienceFormula", "100 + level * 25 + evolution * 100")` is a hardcoded literal, NOT derived from `config.progression()`. The formula source text is not retained: `progression()` at line 114 compiles it into a lambda and `ProgressionConfig` (`omnipet-core/.../progression/ProgressionConfig.java:10`) stores only `ExperienceFormula`. So an operator's custom formula would be silently reverted to the stock string if `encode()` ever rewrote their file. Reachability is narrow: `encode()` runs only on legacy migration (`OmniPetPlugin.java:392-393`), and a legacy file (root keys `globalMaxSlots`/`slotPermission` only, `Phase4PaperConfigLoader.java:20`) has no `progression:` section by definition — so today the loss is theoretical, not live. Still a latent trap if `encode()` gains a second caller.
2. `OmniPetConfigLoader.java:81-83` — `integrations` values are likewise hardcoded literals, so a migration write resets them. Harmless in practice because nothing reads them (finding 1).

## 4. Messages catalog status

**No `messages.yml` is shipped as a resource** — `omnipet-paper/src/main/resources/` contains exactly `config.yml` and `paper-plugin.yml`.

But it is **not code-only in the "nothing on disk" sense**: a file IS written to disk on first run.

- Defaults live in the enum `omnipet-paper/src/main/java/io/github/salyvn/omnipet/paper/text/MessageKey.java` (196 constants).
- `text/MessageCatalogFile.java:38-44` `writeDefaultsIfAbsent()` generates the full document (header at 20-33, body at 47-68) when the file is absent, and returns early if present (line 41).
- Called at `OmniPetPlugin.java:137-138`, immediately before `Messages.bind(loadMessages())` at 139.
- Loaded leniently at `text/MessageCatalog.java:53-58`; reload re-reads at `OmniPetPlugin.java:346,369`.
- The "lenient messages.yml precedent" the README/config comments invoke is real and cited in code: `OmniPetConfigLoader.java:29-31` and `GuiConfigLoader.java:11-18` both name it as the model for the lenient `gui:` path.
- Deliberate design, documented at `MessageCatalogFile.java:14-17`: generated from the enum so there is no second copy of the text to drift. Documented for operators at `docs/configuration.md:60-72` and `docs/troubleshooting.md:24-26`.

Verdict: not a gap. Enum-sourced defaults + first-run generation, no shipped resource by design.

## 5. Example tree status (`src/main/resources/example/*`)

**Orphaned dead weight. Safe to delete.**

- Root `src/` is in no source set: `settings.gradle.kts:23` includes only `omnipet-core` and `omnipet-paper`; root `build.gradle.kts:3-5` applies only the `base` plugin (no `java`), so root `src/` is never compiled or packaged. No `srcDir` override exists in any build script.
- The only code referencing it is the pre-rewrite plugin `src/main/java/io/github/salyvn/omnipet/OmniPetPlugin.java:188-195` (`saveResource("example/config.yml", ...)` etc.), which is itself in the same orphaned tree.
- Contents: `config.yml`, `eggs.yml`, `gui.yml`, `items.yml`, `lang.yml`, `MANUAL.md`, `pets/{example_pet,nahara}.yml` — all legacy schemas. `docs/configuration.md:23` states `items.yml`, `gui.yml`, `lang.yml` "are not current gameplay configuration contracts".
- Docs already disclaim it: `docs/examples.md:3` — "The legacy root `src/main/resources/example/` tree is not packaged by `omnipet-paper` and is not a copy-safe runtime starter."
- No build script, CI workflow (`.github/workflows/build.yml`), or verification task references it. `checkBranding` (`build.gradle.kts:61-62`) and `checkCoreBoundary` (line 29) deliberately scope to `omnipet-core/src` and `omnipet-paper/src` only — root `src/` is not even linted.

Note: the legacy `src/main/resources/example/lang.yml` + `src/main/java/io/github/salyvn/omnipet/language/*.java` are the pre-rewrite message system, superseded by `MessageKey`/`MessageCatalog`.

## 6. Inaccurate comments

### FINDING — `gui.help.linesPerPage` is NOT restart-only

config.yml:99 claims `RESTART ONLY - /pet admin reload will not apply a change here.` The code contradicts this: it is a **per-call read and reloads live**.

- `command/CommandHelp.java:52-55` — `page(List, int)` calls `GuiSettings.gui().helpLinesPerPage()` on every invocation.
- That method is the one the command path uses: `command/CommandHelpRenderer.java:33` → `command/OmniPetCommand.java:341`. No value is cached at construction; `CommandHelp` is a static utility with a private constructor (`CommandHelp.java:25`).
- `GuiSettings` is rebound on a successful reload at `OmniPetPlugin.java:367` (`GuiSettings.bind(staged.gui())`), and `GuiSettings.java:19` holds it in a `volatile` field.

So editing `linesPerPage` and running `/pet admin reload` DOES apply on the next `/pet help`. Contrast with the two genuinely restart-only keys, both of which read once into a final field.

Same error is repeated in the docs table at `docs/configuration.md:198` ("`gui.help.linesPerPage` | **Restart only.**") and the inline comment at `docs/configuration.md:173`. The `GuiSettings` class javadoc at `GuiSettings.java:15-17` is also slightly off — it names "the vault renderer and the Studio's chat-input service" as the construction-time readers, which is correct and does NOT include help paging, so the class doc is consistent with live behavior and only the config/docs claims are wrong.

Note `CommandHelp.LINES_PER_PAGE = 8` (`CommandHelp.java:23`) is a separate test-facing constant, used only by `omnipet-paper/src/test/.../command/CommandHelpTest.java:74,97` — it is not the runtime path.

### Comments verified ACCURATE

- **`gui.vault.petsPerPage` "RESTART ONLY"** (config.yml:95) — correct. `PlayerPetMenuRenderer.java:30-32` reads `GuiSettings.gui().vaultPetsPerPage()` once in its no-arg constructor into `final int petsPerPage` (line 27); the renderer is built in `player/PlayerPetController.java:62`, the controller is constructed once at `OmniPetPlugin.java:184`, and `reloadRuntime()` (343-377) only calls `playerPets.updateLimitsResolver(...)` (line 353) — never rebuilds the renderer.
- **"Capped at 45 … 54-slot layout reserves the bottom row"** (config.yml:96) — correct on both counts. Cap: `GuiConfig.java:23` `MAX_VAULT_PETS_PER_PAGE = 45`, enforced twice (record clamp `GuiConfig.java:28`, renderer clamp `PlayerPetMenuRenderer.java:35`) plus a warning at `GuiConfigLoader.java:55`. Inventory size: `PlayerPetMenuRenderer.java:48` `Bukkit.createInventory(holder, 54, ...)`.
- **"Capped at 20"** for linesPerPage (config.yml:99) — correct. `GuiConfig.java:24,29`, warned at `GuiConfigLoader.java:56`.
- **`gui.studio.promptTimeoutSeconds` "RESTART ONLY"** (config.yml:102) — correct. `PetStudioController.java:111,114` read into `final` fields (declared 60-61) in the constructor, with the reason stated at line 110; `PetStudioController.reload()` (121-129) invalidates the stat catalog and reloads the service but does not rebuild `inputs` (built line 109).
- **`gui.studio.autoCreateEgg` "Reloads live"** (config.yml:107) — correct. Read per save at `PetStudioController.java:406`, and `GuiSettings` is rebound at `OmniPetPlugin.java:367`.
- **`autoCreateEgg` writes `eggs/<definitionId>_egg.yml`** (config.yml:105) — correct. ID: `incubation/EggAdminController.java:153` `definitionId + "_egg"`; repository root `incubation/PaperIncubationServices.java:51` `root.resolve("eggs")`; file extension `omnipet-core/.../persistence/YamlEggDefinitionRepository.java:45` `paths.resolveId(id, ".yml")`.
- **"An existing catalog entry is never overwritten"** (config.yml:106) — correct. `EggAdminController.java:145` returns empty when `eggs.read(eggId).isPresent()`.
- **`gui.feedback.*` "Reloads live"** (config.yml:81,84,86,89) — correct. Staged then applied: `OmniPetPlugin.java:349` resolves, `368` calls `feedback.apply(stagedFeedback)`; `feedback/FeedbackService.java:34` field is `volatile`, `50-52` swaps it, and every emit re-reads it (`88-90`).
- **"An unknown sound name warns at load and silences that one category"** (config.yml:89) — correct. Resolution at `OmniPetPlugin.java:384-386` → `feedback/FeedbackSettings.java:45-71`; emit is `current.sound(category).ifPresent(...)` (`FeedbackService.java:92`), so an unresolved category is silent, not fatal.
- **`gui:` "delete it and OmniPet behaves exactly as it did before"** (config.yml:74-77) — correct. `GuiConfigLoader.java:30` returns `GuiConfig.defaults()` for a null section; `OmniPetConfig.java:19` also null-guards to defaults; `GuiConfig.java:36-42` documents defaults as the previous hardcoded values.
- **`gui:` "an unknown key is ignored with a warning and a bad value falls back to its default"** (config.yml:76-77) — correct. `GuiConfigLoader.java:140-144` `warnUnknown`, plus `bool`/`integer`/`decimal` fallbacks at 151-181. This is the opposite of `storage:`, which throws (`OmniPetConfigLoader.java:162-165`, `Phase4PaperConfigLoader.java:122-129`).
- **`legacyPermission` "Stops at the first missing node and never checks beyond this safe bound"** (config.yml:12) — correct. `permission/LegacyVaultPermissionResolver.java:17-24`: `scanLimit = min(config.maxScan(), MAX_LEGACY_PERMISSION_SCAN)`, loop breaks and returns at the first failing `hasPermission.test(node)` (20-22). Hard ceiling `Phase4PaperConfig.java:19` `MAX_LEGACY_PERMISSION_SCAN = min(PetStorageLimits.MAX_VAULT_CAPACITY, 10_000)`, enforced `Phase4PaperConfig.java:49-52`, plus `maxScan <= maxCapacity` at `Phase4PaperConfig.java:38-40` (shipped 200 == 200, at the boundary but valid).
- **"observations are never persisted"** (config.yml:9) — correct. `LegacyVaultPermissionResolver.Resolution` is a transient return value; effective capacity is recomputed per resolve at `PaperStorageLimitsResolver.java:27-30` and merged read-only against persisted state at `omnipet-core/.../storage/PetStorageLimits.java:60`.
- **`unlocks` "Empty means every player may buy this slot"** (config.yml:28) — correct. `Phase4PaperConfig.java:203-205` `eligible()` returns true on empty permission; empty string accepted by `Phase4ActiveSlotConfigCodec.java:96-98,169-173`.
- **`unlocks` "requires at least one cost"** implied by config.yml:30-31 — enforced `Phase4PaperConfig.java:199`.
- **`storage.activeSlots.multiPetEnabled` "when false, core limits active intent to one pet without deleting stored pets"** (config.yml:15) — correct; flag is passed through to core limits at `PaperStorageLimitsResolver.java:35` and nothing in the resolver deletes.
- **`runtime` "Scheduler changes require a restart"** (config.yml:42) — correct, and the plugin actively tells the operator: `OmniPetPlugin.java:363-365` warns "restart the server to activate them safely" when `runtime` differs after reload. `PaperPetRuntimeCoordinator.java:81` schedules once with `initialDelayTicks`/`periodTicks`.

## Unresolved questions

1. Is `integrations:` intended to stay as inert documentation, or should it be deleted from the shipped file (and from `OmniPetConfigLoader.ROOT_KEYS:22` + `encode():80-83,89`)? Deleting it is the DRY answer since `paper-plugin.yml:10-24` already declares the real dependency contract, but it is operator-visible so removal is a doc-facing change.
2. Fix direction for `gui.help.linesPerPage`: correct the comment/docs to "Reloads live" (matches current code, one-line change ×3 sites), or make it genuinely restart-only for consistency with the other two page-size keys? Recommend correcting the docs — live is the better behavior and costs nothing.
3. Should `encode()` retain the operator's `defaultExperienceFormula` source text (would need `ProgressionConfig` to carry the string alongside the compiled lambda)? Currently unreachable-but-latent.
