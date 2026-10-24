# JVM harness (no Gradle, no Android SDK)

`bash tools/jvm_harness/run.sh [app|bridge|all]` compiles the pure-Kotlin parts of the repo with the same Kotlin version as
`gradle/libs.versions.toml` (2.4.0, downloaded once from the official GitHub release, SHA-256 pinned) and runs the JUnit tests.

- `app`: all app main files that do not import `android.*`/`androidx.*` (+4 files compiled against stubs) and all 47 test files.
- `bridge`: vendored OrbitCore 0.1.0 sources (`orbit-src/commonMain`, `kotlinx.serialization` stubbed) + `:orbitcore-bridge` + its tests
  (real SGP4/SDP4 through the production reflection bridge, Vallado reference vectors).

Evidence class: **JVM-HARNESS**. It does NOT prove: Gradle/AGP wiring, the Compose UI files (`ui/*`), Room (DAOs, migrations, schema),
services/sensors, the published OrbitCore binary, or anything on a device. Stubs (`stubs/`) only replace API surface; they contain no logic under test.
Do not report harness results as Gradle/CI/device results (`NO RECEIPT -> NO EPISTEMIC UPGRADE`).
Receipt: `docs/verification/RECEIPT_2026-10-16_JVM_HARNESS_AND_SGP4.md`.
