# Gradle Wrapper Recovery

This release keeps the standard `gradlew.bat` interface, but it also handles an archive that is missing `gradle/wrapper/gradle-wrapper.jar`.

Behavior:

1. If `gradle-wrapper.jar` exists, the standard Gradle Wrapper is used.
2. Otherwise `gradlew.bat` invokes `scripts/BOOTSTRAP_GRADLE_WINDOWS.ps1`.
3. The bootstrap pins Gradle to 8.11.1 in `.gradle-bootstrap/`.
4. `gradlew.bat` then delegates the original arguments directly to that exact Gradle distribution.

This fallback is only a build bootstrap mechanism. It does not change application code or verification status.


## Gate V3 hardening (2026-09-24)
The candidate archive still does not carry `gradle/wrapper/gradle-wrapper.jar`. Gate V3 now treats that as an explicit packaging condition: it provisions the pinned Gradle 8.11.1 distribution and invokes its `bin\gradle.bat` directly. This prevents the gate report from stopping during wrapper bootstrap before `:app:compileDebugKotlin` actually runs. A standard Gradle project should normally commit the wrapper JAR; this package-level fallback is a controlled recovery path, not a replacement for the standard wrapper layout.
