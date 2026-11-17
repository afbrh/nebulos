#!/bin/bash
# Builds the OpenGOAL native pieces from ../../jak-android-src (branch android-port) and stages them for :core-jak:
#   src/main/jniLibs/arm64-v8a/libjak_main.so       the game (SDL3 static, renamed Java/JNI package)
#   src/main/jniLibs/arm64-v8a/libjak_extractor.so  disc -> game data, an executable run on the device (if built)
#   src/main/assets/jak/{game/assets,game/graphics/opengl_renderer/shaders_es,...}  support files copied to the app's data dir
# Usage: native/build.sh            (everything)    native/build.sh --assets-only
set -e
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${JAK_SRC:-/Users/ry/Desktop/claude/jak-android-src}"
source /Users/ry/Desktop/claude/nebulos/tools-build-env.sh >/dev/null 2>&1 || true
NDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/27.3.13750724"
STRIP="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip"
B="$SRC/build/android-nebulos"
LIBS="$HERE/src/main/jniLibs/arm64-v8a"; ASSETS="$HERE/src/main/assets/jak"
mkdir -p "$LIBS" "$ASSETS"

if [ "$1" != "--assets-only" ]; then
  if [ ! -f "$B/build.ninja" ]; then
    cmake -S "$SRC" -B "$B" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-29 -DCMAKE_BUILD_TYPE=Release "-DCMAKE_C_FLAGS=-march=armv8-a+crc" "-DCMAKE_CXX_FLAGS=-march=armv8-a+crc" \
      -DBUILD_TESTING=OFF -DCURL_ENABLE_SSL=OFF -DSTATICALLY_LINK=ON -DJAK_NEBULOS=ON
  fi
  cmake --build "$B" --target jak_main -j6
  cp "$B/game/libjak_main.so" "$LIBS/libjak_main.so"; "$STRIP" --strip-unneeded "$LIBS/libjak_main.so"
  if [ -f "$SRC/build/android-extractor/libjak_extractor.so" ]; then
    cp "$SRC/build/android-extractor/libjak_extractor.so" "$LIBS/"; "$STRIP" --strip-unneeded "$LIBS/libjak_extractor.so"
  else echo "NOTE: no libjak_extractor.so yet (build/android-extractor): first-launch import will report it missing"; fi
fi

# support files: shaders translated to GLSL ES, game/assets, and whatever else the extractor needs (see README-jak.md)
rm -rf "$ASSETS/game"; mkdir -p "$ASSETS/game/graphics/opengl_renderer"
python3 "$SRC/android-app/tools/translate_shaders.py" "$SRC/game/graphics/opengl_renderer/shaders" "$ASSETS/game/graphics/opengl_renderer/shaders_es" \
  /Users/ry/Desktop/claude/spirv-cross-src/build/spirv-cross
cp -R "$SRC/game/assets" "$ASSETS/game/assets"
# the extractor reads these from --proj-path at runtime (goal_src is compiled, decompiler config drives the decompile, custom assets are merged)
rm -rf "$ASSETS/goal_src" "$ASSETS/decompiler" "$ASSETS/custom_assets"
mkdir -p "$ASSETS/goal_src" "$ASSETS/decompiler/config" "$ASSETS/custom_assets"
cp -R "$SRC/goal_src/common" "$SRC/goal_src/jak1" "$SRC/goal_src/jak2" "$SRC/goal_src/jak3" "$SRC/goal_src/goal-lib.gc" "$SRC/goal_src/goos-lib.gs" "$ASSETS/goal_src/"
cp -R "$SRC/decompiler/config/jak1" "$SRC/decompiler/config/jak2" "$SRC/decompiler/config/jak3" "$ASSETS/decompiler/config/"
for g in jak1 jak2 jak3; do [ -d "$SRC/custom_assets/$g" ] && cp -R "$SRC/custom_assets/$g" "$ASSETS/custom_assets/$g"; done
echo "staged: $(du -sh "$LIBS" | cut -f1) libs, $(du -sh "$ASSETS" | cut -f1) assets"
