# SDK Bootstrap Fix

The Android CLI (`android.exe`) can terminate on Windows while installing platform-tools. This release intentionally does not depend on that executable for SDK installation.

The bundled `BOOTSTRAP_ANDROID_SDK_WINDOWS.ps1` uses the official Android SDK Command-line Tools archive and `sdkmanager`, verifies its published SHA-256, installs:

- platform-tools
- platforms;android-36
- build-tools;36.0.0

It then writes `local.properties` with the detected SDK path.

Source: Android Developers documentation for Command-line Tools and sdkmanager.
