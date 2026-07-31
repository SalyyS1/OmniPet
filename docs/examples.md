# Examples

These examples match contracts accepted by the current Gradle-built JAR. Pet and storage examples are Paper-wired; the egg example is a verified dependency-neutral core contract only. The legacy root `src/main/resources/example/` tree is not packaged by `omnipet-paper` and is not a copy-safe runtime starter.

## Minimal schema 2 definition

Save as `plugins/OmniPet/pets/trailblazer.yml`:

```yaml
schemaVersion: 2
definitionId: trailblazer
revision: 0
classification:
  tier: D
icon:
  head:
    source: TEXTURE_URL
    value: "https://textures.minecraft.net/texture/dd871e28db04d3711792e0fa549e997f846ac950412ba091a606f324459d38d3"
display:
  provider: HEAD
  model: null
```

The ID is the filename without `.yml`. `HEAD` is persisted authoring metadata and supplies a Studio/vault icon; it does not summon or render a live companion.

## ModelEngine metadata

Schema 2 accepts ModelEngine metadata:

```yaml
display:
  provider: MODELENGINE
  model: ember_fox
```

This is storage/editor support only. No ModelEngine adapter or fallback Paper renderer consumes it in the current JAR, so do not advertise a visible model.

## Canonical schema 1 egg definition

Save as `plugins/OmniPet/eggs/tier_d_egg.yml`:

```yaml
schemaVersion: 1
eggId: tier_d_egg
tier: D
baseDuration: 1h30m
candidates:
  - definitionId: ember_fox
    weight: 3
  - definitionId: stone_wolf
    weight: 1
```

The filename must match `eggId`. `baseDuration` accepts compact/compound or ISO-8601 duration text, and candidate weights must have a positive total. The core catalog enforces a 64 KiB limit per file and a 10,000-entry discovery bound.

This example does not start gameplay in the current Paper plugin. The item/PDC bridge, online scheduler, recovery executor, hatch commands/GUI, and live claim path are not wired.

## Active-slot pricing

```yaml
storage:
  activeSlots:
    multiPetEnabled: true
    base: 1
    max: 3
    entitlement:
      mode: HYBRID
      precedence: REQUIRE_BOTH
      luckPermsPermissionTemplate: "omnipet.slot.unlocked.%s"
    unlocks:
      "2":
        permission: ""
        costs: { VAULT: 25000, PLAYER_POINTS: 50 }
      "3":
        permission: "omnipet.slot.purchase.3"
        costs: { VAULT: 75000 }
```

When both prices are available, `/pet slot` presents separate choices and never auto-selects currency. This code path has automated evidence but still requires live certification against the exact Vault, economy, PlayerPoints, and LuckPerms builds used by the server.

## Recovery command

List actionable or unreadable journal entries:

```text
/pet admin transactions 20
```

If OmniPet prints a next cursor, paste that opaque token unchanged:

```text
/pet admin transactions 20 <next-cursor-from-output>
```

The list limit is 1-50. Each journal file read is capped at 16 KiB, and each page reports at most 20 unreadable issues before an omission summary.

After checking the external ledger, apply only the matching decision:

```text
/pet admin reconcile <transaction-uuid> charge
/pet admin reconcile <transaction-uuid> no-charge
/pet admin reconcile <transaction-uuid> refund
/pet admin reconcile <transaction-uuid> sync
```

`sync` verifies the local entitlement and retries only idempotent external entitlement synchronization. It does not withdraw currency again. A migrated schema 1 completion is rewritten as schema 2 after successful persistence, retaining `.bak`.

## Not shipped

Do not copy legacy examples that imply any of these work in the current JAR:

- egg issue commands, Paper incubation scheduling/checkpoints, hatch GUI/commands, or live claim orchestration;
- summoned pets, Paper display entities, ModelEngine rendering, or movement;
- food, evolver, egg, or hatcher item commands/integrations;
- stamina, triggers, expressions, MythicMobs execution, or MythicLib runtime buffs;
- progression, cultivation, release rewards, or player-management gameplay.

The Studio may persist some future-facing metadata, but persistence is not runtime execution. Track implementation gates in [Roadmap](roadmap.md).
