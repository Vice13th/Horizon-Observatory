# Contributing

1. Read `AGENTS.md` and `docs/HORIZON_MASTER_ROADMAP.md` first.
2. Branch from `main`: `feat/<topic>` or `fix/<topic>`.
3. Run `./gradlew :app:testDebugUnitTest` before opening a PR.
4. Never weaken evidence/integrity guarantees (no destructive Room migrations, no silent data rewriting).
5. Update `docs/verification/VERIFICATION_MATRIX.md` whenever a gate changes state; do not mark a gate verified without executed evidence.
