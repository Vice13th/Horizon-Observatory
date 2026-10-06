# HORIZON — Build and Device Validation

## Windows environment used in the verified workflow

- Gradle: 8.11.1
- JDK: Eclipse Adoptium 17.0.20.1
- Android SDK: local Windows SDK installation
- Target physical device: Samsung SM-A075F
- Android: 16 / API 36

## Compile

```powershell
Set-Location "<HORIZON_PACKAGE_ROOT>\PROJECT"
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected gate: `BUILD SUCCESSFUL`.

## Unit tests

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

## Debug APK

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

## Install

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

## Launch

```powershell
& $adb shell am force-stop horizon.observatory.debug
& $adb shell monkey -p horizon.observatory.debug 1
```

## Runtime evidence capture

```powershell
& $adb logcat -c
# Start a recording session in HORIZON.
Start-Sleep -Seconds 60
& $adb logcat -d -v time |
  Select-String -Pattern "horizon.observatory|HorizonHealth|HorizonService|Gnss|GNSS|CellInfo|Telephony|Observation|Session|Recording|COMPLETED|FAILED|Export|FATAL EXCEPTION|AndroidRuntime" |
  Set-Content ".\HORIZON_RUNTIME.txt" -Encoding UTF8
```

## Release boundary

A compile success is not a release verification. Release requires build, install, runtime acquisition, persistence, export, read-back and physical-device evidence.

> Paths in this document are placeholders; no user-specific machine path is required.
