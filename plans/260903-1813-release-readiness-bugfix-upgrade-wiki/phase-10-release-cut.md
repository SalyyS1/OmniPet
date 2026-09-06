---
phase: 10
title: "Release cut"
status: pending
priority: P1
effort: "0.5d"
dependencies: [9]
---

# Phase 10: Release cut

## Overview

Turn the certified build into a published 3.0.0: version bump off SNAPSHOT, LICENSE decision applied, CHANGELOG finalized, metadata polished, tag pushed, artifact attached to a GitHub release.

## Key Insights

- Repo currently has no LICENSE file, no git tags, and `paper-plugin.yml` still says "OmniPet greenfield foundation." **Decided (validation):** ship an All Rights Reserved LICENSE — proprietary text granting no redistribution rights, consistent with the pending license-gate plan. That plan's *gate* remains out of scope here.
- The version lives in `gradle.properties:3` (`pluginVersion=3.0.0-SNAPSHOT`), consumed at `build.gradle.kts:8` — bump that file, not the build script. `paper-plugin.yml`'s version is already derived: `processResources` expands it from the project version (`omnipet-paper/build.gradle.kts:44-48`). That same Groovy `expand()` means a literal `$` introduced into `paper-plugin.yml` while rewriting the description breaks the build.
- Phase 8 single-sourced the version for the docs surfaces (landing page, `wiki-content.js` meta), so the bump must go through that mechanism rather than editing three files by hand.
- Verified 2026-09-04: the GitHub repo is **public** (`gh repo view`: `visibility: PUBLIC`, `licenseInfo: null`, no tags). **Decided 2026-09-04 (operator): the repo stays public and ships the All Rights Reserved LICENSE** — source visible, no reuse or redistribution rights. Open question 6 is closed; step 5 is not gated.

## Requirements

- Functional: `3.0.0` (no SNAPSHOT) artifact reproducible from the tag; GitHub release with JAR + changelog notes; plugin list description accurate; All Rights Reserved LICENSE present and referenced from README.
- Non-functional: conventional release commit; no secrets in release notes; dependency locks updated intentionally or untouched.

## Related Code Files

- Modify: `gradle.properties` (`pluginVersion=3.0.0`), `paper-plugin.yml` (product description — mind the Groovy `expand()`; version is already derived), `CHANGELOG.md` (cut Unreleased → 3.0.0 with date), version single-source from Phase 8, `README.md` (release link + license section)
- Create: `LICENSE` (All Rights Reserved)

## Implementation Steps

1. Write the All Rights Reserved LICENSE; reference it from README.
2. Bump `gradle.properties` to 3.0.0; regenerate the wiki stats/version surfaces Phase 8 single-sourced.
3. Finalize CHANGELOG (date, move Unreleased); write operator-facing release notes. They must disclose: tightened permissions and the new `omnipet.admin.stats` node (Phase 5/6), the new outbound network calls and how to disable them (Phase 6), the placed-egg CLAIM/READY-break semantics change (Phase 3), the supported Paper range with its certified-vs-compiled distinction (Phases 4/9), and the one-way migrations plus the backup-before-upgrade instruction (Phase 2's migration contract, `docs/migration.md`).
4. `gradlew clean build`; verify shaded JAR name/contents (including no `org/bstats/` path); report the test-count delta against the Phase 1 baseline with deletions explained.
5. Conventional release commit; tag `v3.0.0`; push branch + tag. The public-repo + ARR combination was confirmed by the operator on 2026-09-04; the remote `origin` is configured and `gh` is authenticated.
6. `gh release create v3.0.0` with JAR + notes (skip gracefully if `gh`/remote unavailable — report exact gap).
7. Post-release: bump to `3.1.0-SNAPSHOT` (or per user preference), reopen roadmap items deferred to the gameplay plan.

## Todo

- [ ] All Rights Reserved LICENSE landed + README license section
- [ ] 3.0.0 bump in `gradle.properties` + docs version surfaces regenerated
- [ ] CHANGELOG + release notes finalized
- [ ] Clean-build verify vs baseline
- [ ] Tag v3.0.0 + GitHub release with artifact
- [ ] Post-release SNAPSHOT bump

## Success Criteria

- [ ] `git checkout v3.0.0 && gradlew clean build` reproduces the released JAR
- [ ] Release notes disclose behavior changes (permissions, network calls, egg semantics, Paper range, migrations)
- [ ] plugin list shows the real product description

## Risk Assessment

- No git remote/`gh` may be configured — release steps degrade to local tag + artifact with an explicit report; do not fake publication.
- All Rights Reserved on a public GitHub repo grants no reuse rights while still exposing source. Confirmed as intended on 2026-09-04; no further gate on the tag push.
