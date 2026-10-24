#!/usr/bin/env bash
# Horizon JVM harness: compiles and runs the pure-JVM parts of the repo WITHOUT Gradle or the Android SDK.
# Evidence class "JVM-HARNESS": says nothing about Gradle/AGP wiring, Compose, Room or devices (see README.md).
# usage: bash tools/jvm_harness/run.sh [app|bridge|all]        (default: all)
# env:   HARNESS_HOME (toolchain cache, default ~/.horizon-harness), JUNIT_JAR, HAMCREST_JAR
# needs: bash>=4, JDK 17+ (java+javac), curl, unzip, sha256sum, JUnit4+Hamcrest jars
#        (Debian/Ubuntu: apt install openjdk-21-jdk-headless junit4 libhamcrest-java)
set -euo pipefail
MODE=${1:-all}
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
H=${HARNESS_HOME:-$HOME/.horizon-harness}
KOTLIN_VER=2.4.0   # = gradle/libs.versions.toml [versions].kotlin
KOTLIN_SHA256=ba1b9e6eb6ddc3275079224f2e9ea4a2b02eef7d59ce2d38404f04b22613c20a   # observed 2026-10-16 from the official release URL; not compared with a JetBrains-published checksum
JSON_TAG=20240303  # = gradle/libs.versions.toml [versions].orgJson
STUBS=$REPO/tools/jvm_harness/stubs
JUNIT=${JUNIT_JAR:-/usr/share/java/junit4-4.13.2.jar}
HAM=${HAMCREST_JAR:-/usr/share/java/hamcrest-core.jar}
for c in java javac curl unzip sha256sum; do command -v "$c" >/dev/null || { echo "missing tool: $c" >&2; exit 3; }; done
[ -f "$JUNIT" ] && [ -f "$HAM" ] || { echo "JUnit/Hamcrest jars not found; set JUNIT_JAR and HAMCREST_JAR" >&2; exit 3; }
mkdir -p "$H"
if [ ! -x "$H/kotlinc/bin/kotlinc" ]; then
  curl -fsSL -o "$H/kotlin.zip" "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VER/kotlin-compiler-$KOTLIN_VER.zip"
  echo "$KOTLIN_SHA256  $H/kotlin.zip" | sha256sum -c -
  unzip -q -o "$H/kotlin.zip" -d "$H"
fi
if [ ! -d "$H/orgjson/org" ]; then   # org.json built from the tagged source (Maven Central is not needed)
  curl -fsSL -o "$H/json.zip" "https://codeload.github.com/stleary/JSON-java/zip/refs/tags/$JSON_TAG"
  unzip -q -o "$H/json.zip" -d "$H"; mkdir -p "$H/orgjson"
  javac -nowarn -d "$H/orgjson" "$H/JSON-java-$JSON_TAG"/src/main/java/org/json/*.java
fi
KC=$H/kotlinc/bin/kotlinc
LIB=$H/kotlinc/lib
CPB=$LIB/kotlinx-coroutines-core-jvm.jar:$H/orgjson:$JUNIT:$HAM
STD=$LIB/kotlin-stdlib.jar
OUT=$H/out; rm -rf "$OUT"; mkdir -p "$OUT"

junit_classes() {  # <test-dir>: package.Class for every *.kt except *Support.kt
  find "$1" -name '*.kt' | sort | while IFS= read -r f; do
    case "$f" in *Support.kt) continue;; esac
    echo "$(grep -m1 '^package ' "$f" | awk '{print $2}').$(basename "$f" .kt)"
  done
}
compile() {  # <log> <args...>
  local log=$1; shift
  if ! "$KC" -nowarn -jvm-target 17 "$@" > "$log" 2>&1; then grep -v 'Picked up' "$log" | head -40 >&2; echo "COMPILE FAILED ($log)" >&2; return 2; fi
}

run_app() {
  local S=$REPO/app/src M=$REPO/app/src/main/kotlin/horizon/observatory
  local -a MAIN EXTRA CONTRACT TESTS
  # Pure sources: files without android/androidx imports (RoomOrbitDataSource needs the Room DAO and is skipped).
  mapfile -t MAIN < <(find "$S/main" -name '*.kt' | sort | while IFS= read -r f; do
    case "$f" in */RoomOrbitDataSource.kt) continue;; esac
    grep -qE '^import (android\.|androidx\.)' "$f" || echo "$f"
  done)
  # Android-importing files that the pure code/tests need; they compile against tools/jvm_harness/stubs/app.
  EXTRA=("$M/storage/entity/Entities.kt" "$M/astronomy/OrbitCatalogEntity.kt" "$M/core/time/TimestampEngine.kt")
  mapfile -t CONTRACT < <(find "$REPO/propagation-contract/src/main" -name '*.kt' | sort)
  mapfile -t TESTS < <(find "$S/test" -name '*.kt' | sort)
  echo "[app] main files=$((${#MAIN[@]}+${#EXTRA[@]}+${#CONTRACT[@]})) test files=${#TESTS[@]} (other Android-dependent main files are NOT compiled)"
  compile "$OUT/app.log" -cp "$CPB" "${MAIN[@]}" "${EXTRA[@]}" "${CONTRACT[@]}" "${TESTS[@]}" "$STUBS"/app/*.kt -d "$OUT/app"
  (cd "$REPO/app" && java -cp "$OUT/app:$CPB:$STD" org.junit.runner.JUnitCore $(junit_classes "$S/test")) 2>&1 | grep -v 'Picked up'
}

run_bridge() {
  local R=$REPO/orbitcore-bridge
  local -a ORBIT BR
  mapfile -t ORBIT < <(find "$REPO/orbit-src/commonMain" -name '*.kt' | sort)
  mapfile -t BR < <(find "$REPO/propagation-contract/src/main" "$R/src" -name '*.kt' | sort)
  echo "[bridge] OrbitCore source files=${#ORBIT[@]} (vendored 0.1.0 sources, kotlinx.serialization stubbed); bridge+contract+test files=${#BR[@]}"
  compile "$OUT/orbit.log" -opt-in=kotlin.time.ExperimentalTime "$STUBS"/orbitcore/*.kt "${ORBIT[@]}" -d "$OUT/orbit"
  compile "$OUT/bridge.log" -cp "$OUT/orbit:$CPB" "${BR[@]}" -d "$OUT/bridge"
  (cd "$R" && java -cp "$OUT/bridge:$OUT/orbit:$R/src/test/resources:$CPB:$STD" org.junit.runner.JUnitCore $(junit_classes "$R/src/test/kotlin")) 2>&1 | grep -v 'Picked up'
}

case "$MODE" in
  app) run_app;;
  bridge) run_bridge;;
  all) run_app; run_bridge;;
  *) echo "usage: $0 [app|bridge|all]" >&2; exit 2;;
esac
echo "JVM_HARNESS_DONE mode=$MODE"
