# Horizon Parallel â€” Release README

Status: engineering / field-test candidate; not scientifically release-closed.
Variant: Parallel Debug; package horizon.observatory.parallel.debug; version 1.0.0-observatory; min SDK 26; target SDK 36.
APK: artifacts/release/Horizon-Parallel-debug-20261018.apk
Size / SHA-256: 10900108 / EE8204D1BD0D56FAD00C3C4D4D659AE5FDDB7FE4164A8E4189D0CFA29A00B4FE
Signer certificate SHA-256: 1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6

Recorded JVM suite: 352 tests, 0 failures, 0 errors, 3 skipped, 52 XML suites. Release-tree assemble succeeded. APK was installed after signer matching and the pulled device copy hash-matched on Samsung SM-A075F / Android 16 / API 36. Persisted-session export with 93,754 observations passed ZIP CRC, 13/13 internal hashes, JSON/CSV/SQLite count reconciliation, sequence continuity and SQLite integrity.

Private session ZIP is excluded. Source timestamps regress in 44,938/93,754 observations (~47.93%); root cause unresolved. Memory/endurance, prior LOW_MEMORY exits, three skipped SGP4 vectors, Room v5/v6 baselines, latest connected instrumentation, outdoor ground-truth GNSS, controlled interference/multipath tests, authoritative satellite mapping, SAF save flow and clean-machine reproducibility remain open. Parallel does not package OrbitCore; operational SGP4/SDP4 prediction is not claimed.
