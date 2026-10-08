#!/usr/bin/env bash
# Downloads the two libretro cores Titan Slot runs, as arm64 Android builds from the official
# libretro buildbot, into their on-demand feature modules (core_gambatte, core_mgba). The Play
# bundle needs them there; direct builds download the same files on the phone during setup.
#
#   scripts/fetch-cores.sh            # nightly builds
#   BUILDBOT=<url> scripts/fetch-cores.sh
#
# Android only extracts native libraries named lib*.so, so each core is renamed with a
# "lib" prefix; dev.titanslot.core.Core expects exactly these names.
set -euo pipefail

BUILDBOT="${BUILDBOT:-https://buildbot.libretro.com/nightly/android/latest/arm64-v8a}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

CORES=(gambatte mgba)

for core in "${CORES[@]}"; do
  zip="${core}_libretro_android.so.zip"
  out="$ROOT/core_${core}/src/main/jniLibs/arm64-v8a"
  echo "==> $core"
  mkdir -p "$out"
  curl -fL --retry 3 -o "$TMP/$zip" "$BUILDBOT/$zip"
  unzip -o -q "$TMP/$zip" -d "$TMP/$core"
  so="$(find "$TMP/$core" -name '*.so' | head -n 1)"
  if [[ -z "$so" ]]; then
    echo "no .so inside $zip" >&2
    exit 1
  fi
  dest="$out/lib${core}_libretro_android.so"
  cp "$so" "$dest"

  # Google Play wants native code aligned for 16 KB memory pages (apps targeting Android 15+).
  if command -v readelf >/dev/null 2>&1; then
    for align in $(readelf -lW "$dest" | awk '$1 == "LOAD" { print $NF }' | sort -u); do
      if (( align < 0x4000 )); then
        echo "warning: $core's segments are aligned to $align, not 16 KB (0x4000);" \
          "Play will reject the bundle until the core is rebuilt with -Wl,-z,max-page-size=16384" >&2
      fi
    done
  fi
  ls -lh "$dest"
done
