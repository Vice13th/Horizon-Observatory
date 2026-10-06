# Horizon SDK / Build Recovery

## Windows bootstrap

Run from `horizon_work`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\BOOTSTRAP_ANDROID_SDK_WINDOWS.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\BUILD_WINDOWS.ps1
```

The SDK bootstrap uses the official Android CLI package (`Google.AndroidCLI`) and refreshes the current PowerShell process PATH after installation. It also resolves the Windows App Execution Alias and installed AppX package directly, so a new PowerShell window is normally not required.

Required SDK components:

- `platform-tools`
- `platforms/android-36`
- `build-tools/36.0.0`

The project writes `local.properties` with the resolved SDK path.
