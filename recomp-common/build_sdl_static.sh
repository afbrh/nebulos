#!/bin/bash
# Builds SDL3 3.4.16 (the version BOTWRecompAndroid/TOTKDroid pin, with their aaudio patch) as a STATIC library whose Java
# package is renamed org.libsdl.app -> com.noryan.recompsdl, so the BotW/TotK cores don't clash with ARMSX2's SDL3 classes.
# Requires android/deps/build_deps.sh to have run in ../../botwrecomp-src (it unpacks + patches SDL in android/deps/work/sdl).
# Output: <botwrecomp-src>/android/deps/out-static/{lib/libSDL3.a,include}. Also (re)generates src/main/java/com/noryan/recompsdl.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
D=${BOTW_SRC:-$HERE/../../botwrecomp-src}/android/deps
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
NDK=${ANDROID_NDK:-$SDK/ndk/30.0.16248370}
CM=${CMAKE_DIR:-$SDK/cmake/3.31.6/bin}
W=$D/work/sdl-ren; O=$D/out-static
rm -rf "$W" && cp -R "$D/work/sdl" "$W" && rm -rf "$W/build"
grep -rlE 'org[_/.]libsdl[_/.]app' "$W/src" "$W/include" "$W/android-project" 2>/dev/null | while read -r f; do
  sed -i '' -e 's#org/libsdl/app#com/noryan/recompsdl#g' -e 's#org_libsdl_app#com_noryan_recompsdl#g' -e 's#org\.libsdl\.app#com.noryan.recompsdl#g' "$f"
done
J=$HERE/src/main/java/com/noryan/recompsdl
rm -rf "$J" && mkdir -p "$J" && cp "$W"/android-project/app/src/main/java/org/libsdl/app/*.java "$J/" 2>/dev/null || cp "$W"/android-project/app/src/main/java/com/noryan/recompsdl/*.java "$J/"
"$CM/cmake" -S "$W" -B "$W/build" -G Ninja -DCMAKE_MAKE_PROGRAM="$CM/ninja" -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_INSTALL_PREFIX="$O" -DCMAKE_INSTALL_LIBDIR=lib \
  -DSDL_SHARED=OFF -DSDL_STATIC=ON -DSDL_TESTS=OFF -DSDL_EXAMPLES=OFF
nice -n 15 "$CM/cmake" --build "$W/build" -j3
"$CM/cmake" --install "$W/build"
ls "$O/lib"
