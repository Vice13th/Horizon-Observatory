## What and why

## Evidence (label each claim: VERIFIED / OBSERVED / MEASURED / INFERRED / UNVERIFIED)
- [ ] `./gradlew :app:testDebugUnitTest` executed â€” result:
- [ ] Device/emulator checks (if UI or acquisition changed):
- [ ] Verification matrix updated (`docs/verification/VERIFICATION_MATRIX.md`) only with executed evidence

## Invariants
- [ ] No guessed identities/positions; missing data stays "NOT AVAILABLE", never 0
- [ ] Observed and predicted layers stay separate
- [ ] No destructive Room migration; no weakened tests
