# Installation identity table

All variants use namespace horizon.observatory. IDs below are composed from defaultConfig.applicationId, flavor suffixes, and the debug build-type suffix. Verify effective package IDs from generated APK metadata after build; do not infer update compatibility across different signing keys.

| Variant | Flavor suffix | Debug suffix | Release ID | Debug ID | Launcher/theme/authority |
|---|---|---|---|---|---|
| primary | none | .debug | horizon.observatory | horizon.observatory.debug | Default Observatory launcher and theme; primary resilient-location authority/permission placeholders |
| ts | .ts | .debug | horizon.observatory.ts | horizon.observatory.ts.debug | TS label, default launcher/theme, TS-specific authority/permission placeholders |
| t1 | .t1 | .debug | horizon.observatory.t1 | horizon.observatory.t1.debug | T1 label, default launcher/theme, T1-specific authority/permission placeholders; OrbitCore bridge dependency is T1-only |
| parallel | .parallel | .debug | horizon.observatory.parallel | horizon.observatory.parallel.debug | Parallel launcher assets, Parallel theme, Parallel-specific authority/permission placeholders |

primary, ts, and t1 preserve the established destination identities. parallel is the approved additional installation identity. Release build signing is not configured as production signing by this migration. APK signing certificates and installed-package update compatibility must be checked independently.

## Build artifact verification — 2026-10-24

Measured package IDs/labels were read from all eight generated APKs. The four debug artifacts have the same observed debug signer certificate. The four release APKs are unsigned. See docs/migration/APK_ARTIFACT_MATRIX.csv for exact byte lengths and SHA-256 hashes. No release signing or device installation was performed.
