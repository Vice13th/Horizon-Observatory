# HORIZON — Next Build Gate

The source-level automation stage is complete for the current GNSS domain increment.

Run on the Windows project root:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Then:

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Then install the generated APK on the physical SM-A075F and run one new recording session.

Expected new export entries:

- `gnss_domain_snapshot.json`
- `satellite_evidence.csv`
- `gnss_antenna_evidence.json`

The raw `observations.json`, `observations.csv`, and `horizon_session.sqlite` remain the source-of-truth persistence artifacts. Derived GNSS files are analysis products and are not replacements for raw evidence.
