#!/usr/bin/env bash
# Downloads the five libretro cores Titan Slot ships with, as arm64 Android builds from
# the official libretro buildbot, and drops them where Gradle packages native libraries.
#
#   scripts/fetch-cores.sh            # nightly builds
#   BUILDBOT=<url> scripts/fetch-cores.sh
#
# Android only extracts native libraries named lib*.so, so each core is renamed with a
# "lib" prefix; dev.titanslot.core.Core expects exactly these names.
set -euo pipefail

BUILDBOT="${BUILDBOT:-https://buildbot.libretro.com/nightly/android/latest/arm64-v8a}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

CORES=(gambatte mgba melonds fceumm snes9x)

mkdir -p "$OUT"
for core in "${CORES[@]}"; do
  zip="${core}_libretro_android.so.zip"
  echo "==> $core"
  curl -fL --retry 3 -o "$TMP/$zip" "$BUILDBOT/$zip"
  unzip -o -q "$TMP/$zip" -d "$TMP/$core"
  so="$(find "$TMP/$core" -name '*.so' | head -n 1)"
  if [[ -z "$so" ]]; then
    echo "no .so inside $zip" >&2
    exit 1
  fi
  cp "$so" "$OUT/lib${core}_libretro_android.so"
done

echo
ls -lh "$OUT"
