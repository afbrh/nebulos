#!/bin/zsh
# Builds libdarkchronicle_main.so (ChronicleTwo's Android port: CMake + Cargo + the NDK) and stages it where :core-darkchronicle picks it up.
# Usage: ./build_native.sh [configure-only]
# Needs: NDK 29 (ANDROID_NDK), cmake 3.31.6 + ninja from the Android SDK, rustup with the aarch64-linux-android target, ~/android-dev/bin/glslangValidator,
# the static SDL3 (botwrecomp-src/android/deps/out-static, renamed Java package), and the Android FFmpeg built by
#   ANDROID_NDK_HOME=$NDK python3 -I tools/kazarov/tools/ffmpeg/kz-ffmpeg --out .kazarov/ffmpeg/android-arm64 --target android-arm64
set -eu
HERE="${0:A:h}"
SRC="${HERE:h:h}/ChronicleTwo"
NDK="${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/29.0.14206865}"
CM="$HOME/Library/Android/sdk/cmake/3.31.6/bin"
B="$SRC/port/build/android"
export PATH="$CM:$HOME/.cargo/bin:$PATH"
TC="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"
# cargo build scripts (the cxx crate) compile C++ with the NDK at API 30: pthread_cond_clockwait needs it
export CC_aarch64_linux_android="$TC/aarch64-linux-android30-clang" CXX_aarch64_linux_android="$TC/aarch64-linux-android30-clang++"
export AR_aarch64_linux_android="$TC/llvm-ar" CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TC/aarch64-linux-android30-clang"
if [ ! -f "$B/build.ninja" ] || [ "${1:-}" = "configure-only" ]; then
  cmake -S "$SRC" -B "$B" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 \
    -DANDROID_STL=c++_static -DPLATFORM=ANDROID -DCMAKE_BUILD_TYPE=Release -DCT_ENABLE_TRACY=OFF -DCT_BUILD_TESTS=OFF \
    -DCT_NEBULOS_SDL3_STATIC="${HERE:h:h}/botwrecomp-src/android/deps/out-static" -DCT_NEBULOS_VPAD_SOURCE="$HERE:h/recomp-common/src/main/cpp/recomp_vpad.c" \
    -DGLSLANG_VALIDATOR="$HOME/android-dev/bin/glslangValidator" -DKZ_FFMPEG_DIR="$SRC/.kazarov/ffmpeg/android-arm64"
  [ "${1:-}" = "configure-only" ] && exit 0
fi
cmake --build "$B" --target darkchronicle -j "${JOBS:-8}" -- ${KEEP_GOING:+-k 0}
mkdir -p "$SRC/nebulos-jniLibs/arm64-v8a"
cp "$B/libdarkchronicle_main.so" "$SRC/nebulos-jniLibs/arm64-v8a/"
"$TC/llvm-strip" --strip-unneeded "$SRC/nebulos-jniLibs/arm64-v8a/libdarkchronicle_main.so"
ls -l "$SRC/nebulos-jniLibs/arm64-v8a/"
