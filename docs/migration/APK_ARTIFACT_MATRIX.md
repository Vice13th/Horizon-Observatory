# Release artifact matrix - measured 2026-10-24

Release APKs are unsigned. All four debug APKs were freshly verified on 2026-10-24 with Android SDK Build Tools 36.0.0 `apksigner verify --verbose --print-certs`: each returned `Verifies`, exit code 0, and APK Signature Scheme v2 true. The original historical raw output files were not available during this inspection; this status records the fresh rerun against the exact hashes listed below. No release-signing key was used and no device install occurred during migration.

| Variant | Type | Package ID | Label | Bytes | SHA-256 | Signing / verification |
|---|---|---|---|---:|---|---|
| parallel | debug | horizon.observatory.parallel.debug | Horizon Parallel | 10913306 | 81B7079CE4ADA8C5D1EB469C5E72B2E984158EB44BEECCCBA253ED8D880A2992 | DEBUG_SIGNED; apksigner: Verifies (exit 0); v2=true (fresh 2026-10-24) |
| parallel | release | horizon.observatory.parallel | Horizon Parallel | 7392267 | 84290E33ABB20DBDE8468593E3E33AA4ED5DBB788E8B3D909CF40ACC0D777551 | RELEASE_UNSIGNED; unsigned release; signing not configured |
| primary | debug | horizon.observatory.debug | Horizon Observatory | 10913290 | D43734D3758FA5ED6FFAE5BA798F971600254E2243A5DADC3B1CCBF906DA4EEB | DEBUG_SIGNED; apksigner: Verifies (exit 0); v2=true (fresh 2026-10-24) |
| primary | release | horizon.observatory | Horizon Observatory | 7392255 | E0D072C0596BAFFA2DD7A99CE77CEDA27C11FB7E8D3F38DA948AC4C9F6D3C162 | RELEASE_UNSIGNED; unsigned release; signing not configured |
| t1 | debug | horizon.observatory.t1.debug | Horizon Observatory T1 | 11470421 | F37A37D6F393C50B78DC815541416318F752054EE948768ED566848AC2E49B26 | DEBUG_SIGNED; apksigner: Verifies (exit 0); v2=true (fresh 2026-10-24) |
| t1 | release | horizon.observatory.t1 | Horizon Observatory T1 | 7818255 | 8DA742F217E3B7E126B133EA7262C40F2654C9DF5D0688EC6BDFE42D9BAC79E2 | RELEASE_UNSIGNED; unsigned release; signing not configured |
| ts | debug | horizon.observatory.ts.debug | Horizon Observatory TS | 10913310 | B385742D9AFDFD0CA05B6D0C8C496A2BF468B57DCD856651B932F9CCAF7AD433 | DEBUG_SIGNED; apksigner: Verifies (exit 0); v2=true (fresh 2026-10-24) |
| ts | release | horizon.observatory.ts | Horizon Observatory TS | 7392271 | 3D8104AF73CAD408BAA35D10B907D90D9EA6D1C7CB4930F693FAB0182BA20EC7 | RELEASE_UNSIGNED; unsigned release; signing not configured |


## Verification scope

- APKs and metadata were inventoried from migration checkout build output.
- aapt dump badging was used to inspect package ID, label, version name/code.
- SHA-256 was computed from the actual APK bytes.
- Fresh rerun on 2026-10-24: `apksigner verify --verbose --print-certs` returned exit code 0 for each exact debug APK hash below, with v2 signature true and signer certificate SHA-256 `1e14d3b1acb546e0caadb8504e9872fe5242909bfee6c45e4ba4abb4859db5e6`. This is fresh verification, not recovered historical log capture. Release APKs are unsigned pending production signing.
- This matrix is not device acceptance, production signing, or scientific GNSS/RF validation.
