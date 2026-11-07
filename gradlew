#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.13
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DIST_DIR="$GRADLE_USER_HOME/neon-wrapper/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$GRADLE_USER_HOME/neon-wrapper"
  ZIP="$GRADLE_USER_HOME/neon-wrapper/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
    else
      echo "curl is required to download Gradle $GRADLE_VERSION" >&2; exit 1
    fi
  fi
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required" >&2; exit 1; }
  unzip -q -o "$ZIP" -d "$GRADLE_USER_HOME/neon-wrapper"
fi
exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
