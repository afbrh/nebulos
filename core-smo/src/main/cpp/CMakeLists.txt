# libsmo_main.so (+ libsmo_main_<tag>.so per frozen tag): the SMO recomp runtime (../../smorecomp-src) as a Nebulos core.
# Differences from the project's own android/smorecompandroid/app/src/main/cpp/CMakeLists.txt:
#  * SDL3 is linked STATICALLY (renamed Java package, recomp-common/build_sdl_static.sh) - no libSDL3.so / no libmain.so name clash;
#  * the library is named smo_main; recomp_vpad.c (Nebulos' SDL virtual gamepad) is added;
#  * FFmpeg (movies, start-screen background) is left out unless android/deps/out has it (HAVE_FFMPEG).
cmake_minimum_required(VERSION 3.22)
project(nebulos_smo C CXX ASM)

# Runtime source trees (see ../../../../FREEZING.md in the nebulos root): the live tree builds libsmo_main.so; every frozen tag
# (SMO_FROZEN_TAGS, a ;-list of tags; SMO_FROZEN_ROOT = the folder holding one git worktree per tag) builds libsmo_main_<tag>.so
# from that tag's checkout. Build dependencies (SDL static, shaderc, FFmpeg, libadrenotools) always come from the live tree.
set(LIVE ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../smorecomp-src)   # the SMO recomp repository (working tree)
get_filename_component(LIVE ${LIVE} ABSOLUTE)
set(DEPS ${LIVE}/android/deps/out)                     # android/deps/build_deps.sh (headers, volk, shaderc)
set(SDLS ${LIVE}/android/deps/out-static)              # recomp-common/build_sdl_static.sh
set(COMMON ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../nebulos/recomp-common/src/main/cpp)
get_filename_component(COMMON ${COMMON} ABSOLUTE)
if(NOT EXISTS ${SDLS}/lib/libSDL3.a)
    message(FATAL_ERROR "run recomp-common/build_sdl_static.sh first (needs android/deps/build_deps.sh)")
endif()
if(NOT SMO_GEN_DIR)
    message(FATAL_ERROR "SMO_GEN_DIR (generated smaa_hlsl.inc) is not set")
endif()

add_subdirectory(${LIVE}/third_party/libadrenotools ${CMAKE_BINARY_DIR}/adrenotools)

add_library(SDL3 STATIC IMPORTED)
set_target_properties(SDL3 PROPERTIES IMPORTED_LOCATION ${SDLS}/lib/libSDL3.a)
add_library(shaderc_shared SHARED IMPORTED)
set_target_properties(shaderc_shared PROPERTIES IMPORTED_LOCATION ${DEPS}/lib/libshaderc_shared.so)

# FFmpeg 9.0.2 (movies, the start screen's background, TotK's Opus voices): ONE build shared by every Switch recomp core, made by
# ../smorecomp-src/android/deps/build_ffmpeg_local.sh (a macOS-host copy of the project's build_ffmpeg.sh)
set(FFMPEG ${LIVE}/android/deps/ffmpeg)
get_filename_component(FFMPEG ${FFMPEG} ABSOLUTE)
set(HAVE_FFMPEG FALSE)
if(EXISTS ${FFMPEG}/lib/libavformat.so)
    set(HAVE_FFMPEG TRUE)
    foreach(l avcodec avformat avutil swscale)
        add_library(${l} SHARED IMPORTED)
        set_target_properties(${l} PROPERTIES IMPORTED_LOCATION ${FFMPEG}/lib/lib${l}.so)
    endforeach()
endif()

# add_smo_runtime(<target> <source tree>): the runtime built from that tree as lib<target>.so
function(add_smo_runtime NAME R)
    include(${R}/android/gamelib_id.cmake)     # sets GAMELIB_ID
    message(STATUS "${NAME}: runtime from ${R}, GAMELIB_ID ${GAMELIB_ID}")
    file(GLOB RT_SRC CONFIGURE_DEPENDS ${R}/runtime/*.c ${R}/runtime/hle/*.c ${R}/runtime/services/*.c ${R}/runtime/nvn/*.c
         ${R}/runtime/nvn/maxwell/*.c)
    if(NOT HAVE_FFMPEG)
        list(REMOVE_ITEM RT_SRC ${R}/runtime/hle/movie.c)
    endif()
    file(GLOB VK_SRC CONFIGURE_DEPENDS ${R}/runtime/nvn/vulkan/*.c ${R}/runtime/nvn/vulkan/*.cpp)
    # Lossless Scaling frame generation (GPL-3.0-or-later; Nebulos is GPLv3): the runtime's UI refers to its symbols, so it is built in
    file(GLOB LSFG_SRC CONFIGURE_DEPENDS ${R}/runtime/nvn/vulkan/lsfg/*.c)
    list(APPEND VK_SRC ${LSFG_SRC})
    file(GLOB UI_SRC CONFIGURE_DEPENDS ${R}/runtime/ui/*.mm)
    set(IMGUI ${R}/third_party/imgui)
    set(IMGUI_SRC ${IMGUI}/imgui.cpp ${IMGUI}/imgui_draw.cpp ${IMGUI}/imgui_tables.cpp ${IMGUI}/imgui_widgets.cpp
        ${IMGUI}/backends/imgui_impl_sdl3.cpp ${IMGUI}/backends/imgui_impl_vulkan.cpp)

    add_library(${NAME} SHARED ${RT_SRC} ${R}/runtime/platform/android.c ${VK_SRC} ${UI_SRC} ${IMGUI_SRC}
                ${DEPS}/volk/volk.c ${R}/android/common/guest_window.c ${R}/runtime/native_gate.S ${R}/third_party/zstd/zstddeclib.c
                ${COMMON}/recomp_vpad.c)
    set_source_files_properties(${UI_SRC} PROPERTIES LANGUAGE CXX COMPILE_OPTIONS "-xc++")
    set_source_files_properties(${R}/runtime/gamelib.c PROPERTIES COMPILE_DEFINITIONS "GAMELIB_ID=\"${GAMELIB_ID}\"")
    set_source_files_properties(${R}/runtime/gamebuild.c PROPERTIES COMPILE_DEFINITIONS "GAMELIB_ID=\"${GAMELIB_ID}\"")

    target_include_directories(${NAME} PRIVATE ${R}/runtime ${R}/runtime/nvn ${IMGUI} ${IMGUI}/backends ${SDLS}/include ${FFMPEG}/include ${DEPS}/include
                               ${DEPS}/volk ${SMO_GEN_DIR} ${R}/third_party/smaa/Textures ${R}/android/common ${R}/third_party/zstd)
    target_compile_definitions(${NAME} PRIVATE
        VK_NO_PROTOTYPES VK_USE_PLATFORM_ANDROID_KHR IMGUI_IMPL_VULKAN_USE_VOLK
        VMA_STATIC_VULKAN_FUNCTIONS=0 VMA_DYNAMIC_VULKAN_FUNCTIONS=1 TOTK_LSFG)
    if(HAVE_FFMPEG)
        target_link_libraries(${NAME} PRIVATE avcodec avformat avutil swscale)
    else()
        target_compile_definitions(${NAME} PRIVATE LARECOMP_NO_FFMPEG)
    endif()
    target_compile_options(${NAME} PRIVATE -O2 -g -fno-strict-aliasing -Wall -Wno-unused-function -Wno-format
        $<$<COMPILE_LANGUAGE:CXX>:-std=c++17>)
    target_link_libraries(${NAME} PRIVATE SDL3 shaderc_shared adrenotools android nativewindow log dl m OpenSLES GLESv1_CM GLESv2)
    # (runtime/hle.h, ipc.h, main.c: tables in named sections found through __start_ / __stop_ symbols)
    # JNI_OnLoad lives in the statically linked SDL (registers the renamed SDLActivity natives): keep it exported
    target_link_options(${NAME} PRIVATE -Wl,-z,nostart-stop-gc -Wl,-u,JNI_OnLoad -Wl,-z,max-page-size=16384)
endfunction()

if(NOT SMO_SKIP_LIVE)
    add_smo_runtime(smo_main ${LIVE})
endif()
foreach(tag ${SMO_FROZEN_TAGS})
    string(REGEX REPLACE "[^A-Za-z0-9_]" "_" id ${tag})
    if(NOT EXISTS ${SMO_FROZEN_ROOT}/${tag}/runtime)
        message(FATAL_ERROR "frozen runtime ${tag}: no worktree at ${SMO_FROZEN_ROOT}/${tag} (git -C smorecomp-src worktree add --detach ../smorecomp-frozen/${tag} ${tag})")
    endif()
    add_smo_runtime(smo_main_${id} ${SMO_FROZEN_ROOT}/${tag})
endforeach()

# ---- libhactool.so: hactool (ISC, (c) SciresM; ../../hactool-src) as an executable the core runs to take the base game's exefs/romfs out of the
# user's own XCI (SmoGameFiles). Packaged like a library so Android lets the app run it from its native library folder.
set(HT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../hactool-src)
get_filename_component(HT ${HT} ABSOLUTE)
if(EXISTS ${HT}/main.c AND EXISTS ${HT}/mbedtls/CMakeLists.txt)
    set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
    set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
    set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
    set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)
    set(CMAKE_POLICY_VERSION_MINIMUM 3.5)
    add_subdirectory(${HT}/mbedtls ${CMAKE_BINARY_DIR}/mbedtls EXCLUDE_FROM_ALL)
    file(GLOB HT_SRC ${HT}/*.c)
    add_executable(hactool ${HT_SRC})
    target_include_directories(hactool PRIVATE ${HT} ${HT}/mbedtls/include)
    target_compile_definitions(hactool PRIVATE _BSD_SOURCE _POSIX_SOURCE _POSIX_C_SOURCE=200112L _DEFAULT_SOURCE _FILE_OFFSET_BITS=64)
    target_compile_options(hactool PRIVATE -O2 -w)
    target_link_libraries(hactool PRIVATE mbedcrypto m)
    target_link_options(hactool PRIVATE -pie -Wl,-z,max-page-size=16384)
    set_target_properties(hactool PROPERTIES PREFIX "lib" OUTPUT_NAME "hactool" SUFFIX ".so")
endif()
