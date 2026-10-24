# GNSS Shutdown and Export Accounting Verification â€” 2026-10-24

**Disposition: PARTIAL; release acceptance remains BLOCKED.**

## Change under test

- Source shutdown now closes sources, waits for source collector jobs to finish naturally so buffered callbackFlow elements can reach the durable queue, and only cancels a job if the bounded shutdown timeout is reached. A timeout causes `FINALIZATION_INCOMPLETE`, not `FINALIZED`.
- Export validation now compares per-type successful channel acceptance against SQLite persisted rows for finalized GNSS diagnostics. The check excludes types with no callback attempts, including `GNSS_FIX`, because that data is acquired by a separate source.
- Focused unit tests cover buffered channel drain, timeout cancellation, diagnostics arithmetic, finalized accepted-to-persisted mismatch detection, and incomplete finalization semantics.

## Verification

- **MEASURED / PASS:** Fresh forced focused test run on 2026-10-24 â€” `:app:testParallelDebugUnitTest --tests horizon.observatory.acquisition.gnss.GnssDeliveryDiagnosticsTest --tests horizon.observatory.acquisition.service.SourceShutdownTest --rerun-tasks --no-daemon --console=plain`: 6 tests, 0 failures, 0 errors, 0 skipped. (This result predates the two new export-reconciliation unit tests now added; rerun after the current patch.)
- **MEASURED / PASS:** `:app:assembleParallelDebug --no-daemon --console=plain` â€” BUILD SUCCESSFUL, 40 actionable tasks, 4 executed, 36 up-to-date, for the earlier graceful-shutdown-only source state.
- Earlier graceful-shutdown-only APK was 11,039,608 bytes, SHA-256 `1E3F48A8122A7D3B80950D500100EBB025464763E04591D0F5F5D4257D4741EC`. This is historical for that source state, not the final artifact after export-validator changes.
- **OBSERVED:** ADB device list is empty; install/device session test not performed.
- **UNVERIFIED:** Precise root cause for the uploaded session's 27 raw-measurement and 1 status row discrepancy. Graceful shutdown is a plausible mechanism under investigation, not yet proven by a reproduced device session.

## Acceptance gates

1. Rerun focused JVM unit tests after the new export reconciliation tests compile.
2. Compile Android instrumentation source; execute the export integration suite on a connected device.
3. Retrieve the original session ZIP to an approved local audit path and verify its known SHA-256 before sequence-level analysis.
4. Produce a new device session and verify finalized accepted counts reconcile with SQLite rows.
5. Keep GitHub issue #3 open and release acceptance blocked until row-level/fresh-session acceptance succeeds.

Build warnings recorded in the prior run: deprecated Gradle 8.11.1 and Android SDK XML version mismatch; Kotlin deprecation and type-safety warnings also remain. No production signing, phone install, RF test, commit or push is claimed by this receipt.


- **MEASURED / PASS:** Full `:app:testParallelDebugUnitTest --no-daemon --console=plain`: 58 XML suites, 380 tests, zero failures/errors, three skipped; BUILD SUCCESSFUL in 26 seconds.
