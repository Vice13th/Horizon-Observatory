# Horizon Observatory — Master Handoff

**Checkpoint:** 2026-10-24 canonical migration merged. **Status:** merged into `main` at `e6dd76b1ee99e3767c08eed2ce7a41f65732d9a6`; engineering/field-test candidate, not production-ready.

## Canonical source and migration provenance

- Source repository: https://github.com/Vice13th/Horizonparallell
- Source code/build SHA: `0cb51ed64540c4ec12079d1fe6c68c6b6b6769ed`
- Source publication/checkpoint SHA: `498cbdf28ec4a06734b172b8ade84d28a441265a`
- Destination base SHA: `37cecf9d874052b598a1cb64a706119838377b99`
- Migration branch: `migration/horizon-parallel-canonical-2026-10-24`
- Merge PR: https://github.com/Vice13th/Horizon-Observatory/pull/9 (merged 2026-10-24)
- Canonical `main` merge commit: `e6dd76b1ee99e3767c08eed2ce7a41f65732d9a6`
- Destination project root: repository root (not `source/Horizon-Parallel/`).

## What changed

The approved Parallel app, tests, Android resources, Gradle configuration, `propagation-contract`, and `orbitcore-bridge` were imported as a reviewed tree snapshot. Source Git history was not merged into the public destination because the source repository contains private/local evidence and historical blobs that have not been reviewed for public reachability. The provenance record retains exact source and destination SHAs without falsely claiming common ancestry.

## Evidence and blockers

Two controlled live GNSS sessions on 2026-10-24 passed archive-integrity and accepted-to-persisted reconciliation checks at the reported scope. Session 1 had 63 explicit bounded-channel-full rejects; session 2 had none. A historical 27 raw + 1 status persisted-row discrepancy was not reproduced, but its root cause remains unverified and issue #3 remains open. Authentic Room v5/v6 schemas, sustained load/shutdown/process-death tests, API 26–28 physical-device coverage, scientific ground truth/calibration, SGP4 reference vectors, production signing, and clean-machine reproducibility remain open.

## Required next steps

1. Treat PR #9 as merged; do not repeat the migration merge.
2. Keep raw session archives, local screenshots/XML, device serials, and machine paths out of public artifacts.
3. Preserve issue #3 and all scientific/release blockers until independent evidence closes them.
4. Before a release, close or explicitly accept the scientific, persistence, device-matrix, production-signing, and clean-machine reproducibility gates.

## Rollback

Revert the migration commit(s) on the destination branch/PR using ordinary `git revert`; do not rewrite history. The original source repository remains intact. Exact commit IDs and recovery instructions are in `docs/migration/MIGRATION_PROVENANCE.md`.
