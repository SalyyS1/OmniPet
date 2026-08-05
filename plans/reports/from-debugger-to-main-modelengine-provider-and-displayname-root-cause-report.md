# Root cause: MODELENGINE provider forces head icon + renders as HEAD; activated pet has no nameplate

Date: 2026-08-05. Read-only investigation. No files modified, no build run.

---

## BUG C — half 1: head icon is mandatory regardless of provider (WORKING AS DESIGNED, but over-strict + undocumented)

Evidence chain, authoring path:

| Where | Line | What it enforces |
|---|---|---|
| `omnipet-core/.../domain/PetDefinition.java` | 16 | `if (icon == null) throw ... "icon.head is required"` — unconditional, no provider check |
| `omnipet-core/.../domain/HeadIcon.java` | 5-6 | `source` AND `value` must both be non-blank |
| `omnipet-core/.../persistence/PetDefinitionYamlCodec.java` | 29-31 | decode requires `icon`, `icon.head`, `icon.head.source`, `icon.head.value` before it even looks at `display` (line 32-33) |
| `omnipet-core/.../studio/StudioPetDraft.java` | 43 | `if (icon == null) throw ... "icon is required"` |
| `omnipet-core/.../studio/PetDefinitionStudioService.java` | 284-301 | `validateFields`: source must be one of `TEXTURE_URL/BASE64/HEAD_CATALOG`; value must not be `CHANGE_ME`; `BASE64` must decode to valid `textures.SKIN.url` JSON (327-347). **No `display.provider` branch anywhere in this method.** |
| `omnipet-paper/.../studio/bukkit/PetStudioController.java` | 501-503 | new draft is seeded `new HeadIcon("BASE64", "CHANGE_ME")` and `Provider.HEAD` |
| `omnipet-core/.../runtime/RendererAppearance.java` | 13-14 | `fallbackHeadSource` + `fallbackHeadValue` are `require(...)` — non-blank mandatory even when `provider == "MODELENGINE"` |

So: operator picks `MODELENGINE wolf_model` via `EDIT_DISPLAY` (`PetStudioController.java:187-188`), then `SAVE` still fails on
`icon.head.value must be configured` (`PetDefinitionStudioService.java:289-290`) because the seeded sentinel is still there.
There is no provider-conditional relaxation in any of the six layers above.

This is intentional, not accidental — the head icon serves two other jobs:
- it is the **GUI item icon** for the definition in the Studio list and editor (`StudioInventoryRenderer.java:73`, `114-115`) and in the player-facing menus (`PetManagementMenuRenderer.java:114-117`);
- it is the **HEAD-fallback texture** consumed when the ModelEngine adapter is unavailable (`PaperHeadItems.java:45` reads `appearance.fallbackHeadSource()`), which is exactly the path Bug C half 2 lands on.

Classification: **not a code bug**. Requirement is real (icon is load-bearing twice over) but the Studio never explains
*why* a MODELENGINE pet still needs a head, and the wording `icon.head.value must be configured` reads like a
provider mismatch. Fix is UX/doc (label the field "GUI icon + fallback texture"), not validation removal.
`display.model` is separately required for MODELENGINE at `DisplayDefinition.java:6-8`.

---

## BUG C — half 2: MODELENGINE pet activates on the HEAD renderer

### Resolver chain (proven wiring)

1. `PaperRuntimeBootstrap.java:31-34` — `HeadFallbackRendererResolver(new PaperModelEngineRendererResolver(plugin, render), head)`.
   **Candidate "wired with only the head renderer" is RULED OUT** — both are wired.
2. `PetActivationService.java:98-104` — resolves, checks `health().available()`, then `spawn(request)`.
3. `HeadFallbackRendererResolver.java:24-27` — always returns the same `RoutingRenderer`; the real choice happens in `spawn`.
4. `HeadFallbackRendererResolver.java:53-72` — **this is the fallback**:
   - line 55: `if (!"HEAD".equals(request.appearance().provider()))` → try preferred
   - line 57-60: `preferred.resolve(request)`; if `health().available()` → ModelEngine handle
   - line 61-64: any unavailable health **or** any `RuntimeException`/`LinkageError` is captured into `preferredFailure`
   - line 66-67: **unconditionally spawns on `head`**
   - line 68-71: the suppressed `preferredFailure` is only ever rethrown **if the HEAD fallback also fails**. HEAD normally succeeds (`PaperHeadRenderer.java:36` always reports `AVAILABLE`), so `preferredFailure` is **discarded with no log line, no player message, no entry in `PetActivationService` failures map**.

There is no logger in `HeadFallbackRendererResolver` at all. That is why the operator sees a silent HEAD pet.

### Is the provider string correct at the resolver? YES on the normal path

`PaperRuntimeOwnerSnapshot.java:51-55`:
```java
RendererAppearance appearance = new RendererAppearance(
        definition.display().provider().name(),   // -> "MODELENGINE"
        definition.display().model(),             // assetId
        definition.icon().source(),
        definition.icon().value());
```
`RendererAppearance.java:11` uppercases; enum name is already `MODELENGINE`. So `provider()` is correct whenever
the definition is present in the registry snapshot. **Candidate "provider is not MODELENGINE" is RULED OUT for the normal path.**

**But it is CONFIRMED for the persisted-appearance fallback path.** `PaperRuntimeOwnerSnapshot.java:38-47`: if
`registry.definitions().get(instance.definitionId()) == null`, it calls `persistedFallback(instance)` (76-89), which
reads `components.appearance.provider` from storage. That persisted value is **hard-coded `"HEAD"` at both write sites,
never derived from `display.provider`**:
- `omnipet-core/.../incubation/IncubationPetFactory.java:37-40` → `"provider", "HEAD"`
- `omnipet-paper/.../incubation/EggAdminController.java:379-382` → `"provider", "HEAD"`

So any pet whose definition was renamed/archived/failed to load renders as HEAD **permanently and by construction**,
and `HeadFallbackRendererResolver.java:55` short-circuits before even probing ModelEngine. This is a second,
independent defect worth fixing regardless of the primary cause.

### Most likely primary cause: ModelEngine adapter reports QUARANTINED / unavailable

Two sub-cases, both landing on the same silent fallback at `HeadFallbackRendererResolver.java:61`:

**(a) ModelEngine plugin not present or not enabled** — `PaperModelEngineRendererResolver.java:32-33`:
```java
Plugin current = plugin.getServer().getPluginManager().getPlugin("ModelEngine");
if (current == null || !current.isEnabled()) throw new IllegalStateException("ModelEngine is not enabled");
```
Thrown into `preferredFailure`, swallowed. `paper-plugin.yml:19-22` declares ModelEngine `required: false`.

**(b) Reflective binding failed → health QUARANTINED.** `PaperModelEngineRenderer.java:59-65` catches
`ReflectiveOperationException|RuntimeException|LinkageError` in the constructor and sets `unhealthy`;
`health()` (76-81) then returns `QUARANTINED`; `HeadFallbackRendererResolver.java:59-61` sees `!available()` and falls back.
The bindings are **exact-signature** lookups with no tolerance (`ReflectiveModelEngineBindings.java:20-33`):
`ModelEngineAPI.getBlueprint(String)`, `createModeledEntity(Entity)`, `createActiveModel(String)`,
`ModeledEntity.addModel(ActiveModel, boolean)`, `removeModel(String)`, `setBaseEntityVisible(boolean)`,
`ActiveModel.setScale(double)`, `destroy()` x2. A single renamed method or a `float`-vs-`double` signature change in
the installed ModelEngine build throws `NoSuchMethodException` and quarantines the **whole** adapter (unlike the
animation bindings, which are deliberately soft-failed — `ReflectiveModelEngineAnimationBindings.java:26-42`).
`setScale(double)` on `ActiveModel` and `getBlueprint(String)` are the two most version-sensitive of the set.
Docs pin `R4.0.9` (`docs/integrations.md:42`); anything else is unverified — `docs/roadmap.md:41` still marks this
adapter "uncertified", and `docs/compatibility.md:63` even says "no renderer adapter".

**Cannot distinguish (a) from (b) from source alone** — both are erased before they reach a log. Distinguishing them
needs one line of runtime evidence (see unresolved questions).

**Sticky-once-fallen-back aggravator:** the `RoutedHandle` (`HeadFallbackRendererResolver.java:98-114`) captures the
chosen renderer for the pet's whole activation; `update`/`updateAppearance` (75-84) forward to that same renderer.
Nothing re-probes ModelEngine per tick. Only `RendererProviderLifecycleListener.java:21,26` (ModelEngine plugin
enable/disable) triggers `runtime.reload()`. So a pet that once fell back to HEAD stays HEAD.

Note on `PaperModelEngineRenderer.java:87-89` (`spawn` throws when provider != "MODELENGINE"): that guard is never the
trigger here — `PaperModelEngineRendererResolver.java:29-31` already throws on the same condition before spawn, and both
are unreachable on the normal path since the provider string is correct (see above).

### Classification
**Bug** (observability + silent-degradation), plus one **hard defect**: hard-coded `"HEAD"` in the persisted
appearance at `IncubationPetFactory.java:38` and `EggAdminController.java:380`.

---

## BUG D — activated pet has no display name / nameplate

### No nameplate code exists anywhere

Repo-wide grep for `setCustomName` / `customName(` / `setCustomNameVisible` / `CustomNameVisible` / `TextDisplay`
across all `src/main`:

- **Zero** calls on any renderer-owned entity.
  - `BukkitPaperHeadRendererBackend.java:44-52` (`spawnCarrier`, ArmorStand): `setInvisible/setMarker/setGravity/setInvulnerable/setCollidable/setSilent/setPersistent` only — no name.
  - `BukkitPaperHeadRendererBackend.java:57-64` (`spawnVisual`, ItemDisplay): billboard, transform, persistent only.
  - `BukkitPaperHeadRendererBackend.java:67-73` (`spawnInteraction`): responsive, persistent only.
  - `PaperModelEngineRenderer.java:103-116` (ArmorStand carrier + Interaction): same set, no name.
- `PaperHeadRendererBackend.java:9-49` — the backend **interface has no name-related method at all** (`spawnCarrier`, `spawnVisual`, `spawnInteraction`, `attach`, `smoothMove`, `hardTeleport`, `updateAppearance`, `updateScale`, `remove`). A nameplate cannot be set without widening this interface.
- The only `TextDisplay` in the plugin is for **placed incubation eggs**, unrelated to pets: `omnipet-paper/.../incubation/placed/PlacedEggHolograms.java:47-74`.
- Remaining `displayName(...)` hits are **inventory `ItemMeta`**, not entities: `GuiItems.java:62`, `PaperEggItemCodec.java:178`, `IncubationActionItemController.java:170`, `PaperPetConsumableInventory.java:98`.

### The name cannot even reach a renderer

- `RendererSpawnRequest.java:8-14` fields: `ownerId, petInstanceId, rendererGeneration, definitionId, appearance, transform`. **No name.**
- `RendererAppearance.java:5-9` fields: `provider, assetId, fallbackHeadSource, fallbackHeadValue`. **No name.**
- `PetDefinition.java:5-11`: `id, revision, tier, icon, display, rawNode`. **No display-name field.**
- `PetInstance.java:6-11`: `id, definitionId, definitionRevision, rawComponents, extensions`. **No name field.**
- `PaperRuntimeOwnerSnapshot.DesiredPet` (67-72) carries `instance, definitionId, appearance, movement, scale` — the name is not compiled into the runtime input either.

### A name value does exist in the data model, but only for GUI

- `omnipet-core/.../management/PetManagementMetadata.java:10-15,23,31-32` — `customName`, max 48 chars, persisted under `extensions.management.customName`.
- `omnipet-core/.../management/RepositoryPetManagementService.java:34-37` — `rename(...)` exists **but is not exposed**: `PetManagementRepositoryPort.java:18-22` declares only `favorite`, `lock`, `move`; `CorePetManagementRepositoryAdapter.java:39-45` delegates only those three. No caller of `rename` in `src/main` (only `RepositoryPetManagementServiceTest.java:30`).
- Sole read: `PetManagementMenuRenderer.java:113-117` uses `customName` as the **GUI item label**, falling back to `definitionId`.
- `lang/vi.yml` and `MessageKey.java` have **no pet-nameplate key**; every `*-name` key is an item name (`MessageKey.java:255,268,297,301,306,311`).
- Documented as deferred: `README.md:47` and `docs/roadmap.md:46,53` both list "rename control" as not shipped.

### Classification
**Unimplemented feature, not a bug.** Nothing to fix — there is no nameplate code path that fails to fire. Delivering it
requires: a name on `RendererSpawnRequest` (or on `RendererAppearance`), plumbing through
`PaperRuntimeOwnerSnapshot.compile` (51-57), a new `PaperHeadRendererBackend` method, implementations in
`BukkitPaperHeadRendererBackend` and `PaperModelEngineRenderer`, an update path for renames, plus exposing
`rename` on `PetManagementRepositoryPort`. That is a feature change touching a public core port, not a patch.

---

## Recurrence prevention

1. **Log the swallowed fallback.** `HeadFallbackRendererResolver.java:66-71` must report `preferredFailure` (once per
   provider epoch, keyed like `PaperModelEngineRendererResolver.java:39-43` already does for animation) via the
   existing `PaperRuntimeFailureSink` (`PaperRuntimeBootstrap.java:46`). This single gap is why Bug C is undiagnosable
   from a server log today.
2. **Surface renderer identity to the operator.** `ActiveRendererSnapshot` / `RendererCapabilities.model()` already
   distinguish HEAD from ModelEngine; an admin inspect line showing the *actual* renderer per active pet would have
   answered this in one command.
3. **Stop hard-coding `"HEAD"`** in the persisted appearance (`IncubationPetFactory.java:38`, `EggAdminController.java:380`);
   derive from `definition.display().provider().name()`.
4. **Test gap:** `HeadFallbackRendererResolverTest.java:20,42` cover fallback-on-failure and preferred-wins, but no test
   asserts that a fallback is *reported*. Add one.
5. **Studio label gap:** state on the icon tile that the head icon is the GUI icon + fallback texture, so a MODELENGINE
   author does not read the requirement as a bug.

---

## Unresolved questions

1. Which sub-case fires on the reporter's server — ModelEngine absent/disabled (`PaperModelEngineRendererResolver.java:33`)
   or reflection-quarantined (`PaperModelEngineRenderer.java:59-64`)? Source cannot tell; both are swallowed. Need either
   (a) the exact ModelEngine build/version installed, or (b) a temporary log of `preferredFailure`, or (c) `/plugins` output.
2. What ModelEngine version is actually installed vs the pinned `R4.0.9` (`docs/integrations.md:42`)? If it differs,
   `ActiveModel.setScale(double)` and `ModelEngineAPI.getBlueprint(String)` are the likeliest signature breaks.
3. Was the reporter's pet hatched *before* its definition became MODELENGINE? If yes, the persisted `"HEAD"` provider
   (defect 3 above) is the cause and the ModelEngine adapter is never even probed — this is cheap to confirm by reading
   `components.appearance.provider` in the player's stored data.
4. Is the MODELENGINE pet's `display.model` actually loaded in ModelEngine? A missing blueprint throws at
   `PaperModelEngineRenderer.java:100-101` *inside* `spawn`, which is also swallowed into the same silent HEAD fallback.
5. Bug D scope decision needed: nameplate from `PetManagementMetadata.customName` only, or also a definition-level
   default display name (which does not exist in `PetDefinition` today)?
