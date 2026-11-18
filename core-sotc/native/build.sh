#!/bin/bash
# Builds Celosia's Android runtime from ../../celosia-src (branch nebulos-android) and stages it for :core-sotc:
#   src/main/jniLibs/arm64-v8a/libps2EntryRunner.so   the game (recompiled code + runtime + GLES renderer), stripped
#   src/main/jniLibs/arm64-v8a/libc++_shared.so       its C++ runtime
# The recompiled game code itself is generated from the user's disc by celosia-src's `./celosia setup` (Mac, once). Usage: native/build.sh
set -e
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${CELOSIA_SRC:-/Users/ry/Desktop/claude/celosia-src}"
source /Users/ry/Desktop/claude/nebulos/tools-build-env.sh >/dev/null 2>&1 || true
NDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/27.3.13750724"
BIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64"
B="$SRC/build-android"
[ -f "$B/build.ninja" ] || { echo "no $B: configure it first (see core-sotc/README-sotc.md)"; exit 1; }
LIBS="$HERE/src/main/jniLibs/arm64-v8a"; mkdir -p "$LIBS"
ninja -C "$B" ps2xRuntime/libps2EntryRunner.so -j"${JOBS:-3}"
"$BIN/bin/llvm-strip" --strip-unneeded -o "$LIBS/libps2EntryRunner.so" "$B/ps2xRuntime/libps2EntryRunner.so"
cp "$BIN/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$LIBS/"
ls -la "$LIBS"
