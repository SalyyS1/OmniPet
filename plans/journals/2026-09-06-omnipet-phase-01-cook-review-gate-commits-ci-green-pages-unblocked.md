---
date: 2026-09-06
title: "Phase 1 cook: review gate, five commits, matrix green, Pages unblocked"
plan: 260903-1813-release-readiness-bugfix-upgrade-wiki
phase: 1
---

# 2026-09-06 — Phase 1 cook session

Resumed after a two-day gap with the Bash tool dead end to end; everything ran
through PowerShell tabs instead. No lesson about the codebase, several about the
environment.

## What shipped

- Reviewer gate (code-reviewer, read-only): no P1. Two P2s fixed before commit:
  the idle-play loader accepted data-requiring particles (DUST, BLOCK, ITEM,
  ...) that the no-data `spawnParticle` overload rejects per burst, and one
  usage string plus the roadmap still carried pre-rename argument hints. Cheap
  P3s taken: idle-play reload warning, `PARTICLE` failure stage, CHANGELOG
  wording. Report: `plans/reports/from-code-reviewer-to-cook-phase-01-idle-play-and-max-health-hotfix-review.md`.
- Commits: `048838d` feat(runtime) idle play, `91376a4` fix(skills) max-health
  hotfix, `5b54ea6` docs, `faa3f04` docs(plans), `3eb78d2` chore(gitignore).
  `.claude-terminal` and the unapproved license plan stayed out by design.
- CI run 34026139909: gradle + all five compatibility points green. First
  full-matrix green since 2026-08-06; the `GENERIC_MAX_HEALTH` era is over.
- Pages run 34026139915 deployed cleanly (last time it hung in
  `deployment_queued`); live stats file now matches the committed one.

## Friction worth remembering

- The workspace has a background git-status poller that dies holding a
  zero-byte `.git/index.lock`. Git scripts here need retry-with-process-check,
  not bare calls. The commit script in `build/` (ignored) shows the pattern.
- PowerShell splits unquoted `-Pfoo=1.21-R0.1-SNAPSHOT` arguments at the dots
  and turns them into task names; quote Gradle `-P` values.
- `$ErrorActionPreference = 'Stop'` makes native-command stderr a terminating
  error before exit codes can be inspected; wrap git calls in try/catch.

## State handed to Phase 2/4/5

Baseline: 210 suites / 1116 tests, JAR 2,014,399 bytes, SHA-256
`02cc67fd7819b46b1e05090dd4fc591785dfc0c0f6f33c34bf74a4cd50cac537`.
Phase 4 owns `OmniPetPlugin.java` and `paper-plugin.yml` and starts from a
green matrix.
