# Phase 7 docs and verification

Plan: `plans/260803-1146-omnipet-ux-overhaul-help-tab-complete-message-catalog-studio-input-player-hub/`
Recorded: 2026-08-03

## 1. Definitive build

`gradlew.bat clean build --no-daemon --console=plain` — **BUILD SUCCESSFUL**, 18 actionable tasks,
**zero** failures, errors, or skips.

| Module | Suites | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| `omnipet-core` | 63 | 256 | 0 | 0 | 0 |
| `omnipet-paper` | 84 | 349 | 0 | 0 | 0 |
| **Total** | **147** | **605** | **0** | **0** | **0** |

Artifact `build/release/OmniPet-3.0.0-SNAPSHOT.jar` — **1,674,717 bytes**,
SHA-256 `485AC4C6AA25042B3CBD4D2805C4D76B317F37AAACFE7326639D89D86B4299A9`.
1015 entries, 925 classes, one `paper-plugin.yml`.

| Guard | Result |
| --- | --- |
| `checkCoreBoundary` | Green. The one new core file, `HeadIconSources`, imports no Bukkit/Paper/vendor. |
| `checkBranding` | Green. |
| `checkGradleOnly` | Green. |
| `checkDistributionArtifact` | Green. **Zero** `net/kyori/adventure/text/minimessage` entries in the JAR — MiniMessage stays `compileOnly` via paper-api. Zero Maven metadata. |

Baseline was 132 suites / 461 tests / 1,589,478 bytes. Growth: **+15 suites, +144 tests, +85,239
bytes** across the seven phases.

## 2. Manual smoke checklist (live-server certification remains a release gate)

Not run — no live server is attached to this session. Each item is mapped to the automated
assertion that already covers it:

| Smoke item | Automated coverage |
| --- | --- |
| `/pet ` Tab as plain player → vault, hatch, slot, skill, help; no admin | `CommandSuggestionsTest` |
| `/pet ` Tab as admin → admin appears; single-permission admin sees only its branch | `CommandSuggestionsTest` |
| `/pet admin hatch ` Tab → inspect, reduce, set, complete, cancel; inspect gated separately | `CommandSuggestionsTest` |
| `/pet help` page 1 and past the last page | `CommandHelpTest` paging + clamp tests |
| Paste base64 head → accepted, head previews | `HeadIconSourcesTest` (18) + chat→Save cross-check |
| Click stat → modifier screen → `10 50` and `FLAT 10 50` both accepted | `StudioDraftInputParsersTest` |
| Hub → vault → manage → back to hub | `AdminPetCommandParserTest` routing + `HubMenuListener` guards |
| Delete `messages.yml`, restart → regenerates | `MessageCatalogFile` round-trip test |
| Corrupt a value, `/pet admin reload` → warning + previous catalog retained | `MessageCatalog` degrade tests + staged-swap wiring |

**Certification stays deferred** (no live TPS, no vendor-plugin, no crash-injection claims).

## 3. Verified stale claims

Each doc row was verified against the code before editing — see the per-file notes in the docs
commits. The `/pet` contract change is worded identically in `plan.md`, `CHANGELOG.md`, and
`docs/commands-and-permissions.md`.

## 4. Unresolved questions

- Live-server smoke (item 2) is unrun and remains a release gate; the automated equivalents are
  listed above for whoever runs it.
