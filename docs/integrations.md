# Integrations

OmniPet's shipped definition/storage features do not require another plugin. Optional integrations are capability-gated; exact artifact availability is not a live compatibility guarantee.

## Capability matrix

| Capability | Plugin | Current status | Missing-plugin behavior |
| --- | --- | --- | --- |
| Studio stat catalog | MythicLib | Reflection-safe picker with manual fallback | Picker unavailable; manual IDs remain supported. |
| Owner stat application | MythicLib | Deferred | No runtime buff is applied. |
| Item stats/expressions | MMOItems | Deferred | No OmniPet item integration is active. |
| Direct skill execution | MythicMobs | Deferred | No direct adapter is loaded. |
| Live rendering | ModelEngine | Deferred | Definition metadata remains stored; no pet entity is rendered. |
| Decimal economy | Vault + economy service | Reflection-safe balance/withdraw/refund adapter implemented | Vault choice is unavailable; other storage features remain usable. |
| Points economy | PlayerPoints | Reflection-safe UUID/int adapter implemented | PlayerPoints choice is unavailable. |
| External slot nodes | LuckPerms | Reflection-safe idempotent grant/revoke and precedence implemented | OmniPet-authoritative mode remains usable. |

The Paper descriptor declares MythicLib, MMOItems, Vault, PlayerPoints, and LuckPerms as optional server dependencies with isolated classpaths. Economy and LuckPerms use dedicated dynamic registries; no vendor classes appear in core contracts.

## Economy and entitlement behavior

- Vault amounts are bounded decimals that must round-trip through the provider's `double` API. PlayerPoints amounts are bounded integers.
- Provider calls execute through Paper's primary thread bridge. YAML I/O, scans, and saga recovery stay on serialized workers.
- A thrown, timed-out, or null provider result is `UNKNOWN_REQUIRES_RECONCILIATION`, not a retry signal.
- Use `/pet admin transactions`, verify the external ledger, then choose `charge`, `no-charge`, or `refund` for the specific transaction UUID.
- Failed LuckPerms propagation remains `ENTITLEMENT_SYNC_PENDING`. Use `/pet admin reconcile <transaction-uuid> sync`; this verifies the local entitlement and retries only idempotent external synchronization.
- Schema 1 completion rows migrate to the same pending-sync path. No economy operation is replayed; successful persistence rewrites schema 2 and retains `.bak`.

These adapters have unit/compile evidence only. Do not claim compatibility with a specific economy implementation, PlayerPoints build, or LuckPerms build until the live matrix passes.

## Selective lifecycle refresh

Plugin lifecycle changes do not clear every provider:

- Vault itself and the plugin owning Vault's registered economy service affect only `VAULT`.
- PlayerPoints affects only `PLAYER_POINTS`.
- LuckPerms affects only the entitlement adapter.
- An unrelated plugin disable leaves healthy adapters published.
- Enable/disable events queue one coalesced full refresh on the next tick; only adapters passing plugin, service, and ABI probes are republished.
- Full OmniPet shutdown invalidates all adapters, stops the provider-call executor, cancels queued/active sync work, and rejects later calls.

## Deferred integration metadata

Schema 2 definitions can store provider-neutral fields such as MythicLib-oriented stat IDs, opaque skill references, progression metadata, and `display.provider: MODELENGINE`. Admin Pet Studio validates and preserves those values, but persistence does not mean runtime execution.

No current code applies owner stats, casts MythicMobs skills, creates MMOItems, spawns a HEAD/Paper display renderer, or creates a ModelEngine model. Keep a valid head icon for Studio/vault presentation; treat every live gameplay adapter as roadmap work.

## Version hazards

- Vendor snapshots can change without semantic-version stability.
- A Maven coordinate can resolve while still being incompatible with the selected Paper/Minecraft line.
- Direct third-party references in always-loaded classes can fail before a presence check; keep adapters isolated.
- Inventory and provider mutations must remain on the Paper thread boundary defined by the current adapters.

## Release test permutations

Before advertising provider support, exercise at least:

1. No optional plugins.
2. MythicLib absent/present for the Studio picker and manual fallback.
3. Vault only, PlayerPoints only, both currencies, and each provider disabled during confirmation.
4. Disable/re-enable Vault, the registered Vault economy service owner, PlayerPoints, and LuckPerms.
5. Disable an unrelated plugin and confirm healthy providers remain available.
6. Repeat lifecycle events and confirm they coalesce to one next-tick refresh.
7. LuckPerms absent/present with every configured entitlement precedence.
8. Restart with schema 1 completion journals and prove sync causes no second withdrawal.

See [Configuration](configuration.md), [Commands and permissions](commands-and-permissions.md), and [Roadmap](roadmap.md).
