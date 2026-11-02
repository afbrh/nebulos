#!/bin/sh
# Rebuilds src/main/jniLibs/arm64-v8a/libmain.so (needs NDK 29 and a built libdusklight_main.so, only used as the link name).
set -e
cd "$(dirname "$0")"
NDK=${ANDROID_NDK:-$ANDROID_HOME/ndk/29.0.14206865}
CC=$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android30-clang)
ENGINE=${1:-$(cd ../../../dusklight-src/build/android-arm64 && pwd)}
"$CC" -shared -nostdlib -fPIC -Wl,-soname,libmain.so -Wl,--no-as-needed -Wl,--allow-shlib-undefined \
  -Wl,-z,max-page-size=16384 -s -o ../src/main/jniLibs/arm64-v8a/libmain.so libmain_shim.c -L"$ENGINE" -ldusklight_main
