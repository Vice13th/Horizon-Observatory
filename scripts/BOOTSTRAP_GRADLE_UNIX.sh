#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
VERSION=8.11.1
ROOT=".gradle-bootstrap"
ZIP="$ROOT/gradle-$VERSION-bin.zip"
HOME_DIR="$ROOT/gradle-$VERSION"
URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
SHA="f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
mkdir -p "$ROOT"
if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then curl -fL --retry 3 "$URL" -o "$ZIP"; elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL"; else echo "ERROR: curl/wget required to bootstrap Gradle $VERSION" >&2; exit 2; fi
  fi
  ACTUAL=$(sha256sum "$ZIP" | awk '{print $1}')
  [ "$ACTUAL" = "$SHA" ] || { echo "ERROR: Gradle checksum mismatch" >&2; exit 3; }
  TMP="$ROOT/_extract"
  rm -rf "$TMP"
  mkdir -p "$TMP"
  unzip -q -o "$ZIP" -d "$TMP"
  rm -rf "$HOME_DIR"
  mv "$TMP/gradle-$VERSION" "$HOME_DIR"
  rm -rf "$TMP"
fi
"$HOME_DIR/bin/gradle" --version >/dev/null
