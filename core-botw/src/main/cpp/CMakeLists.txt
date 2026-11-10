# libbotw_main.so: the BOTWRecompAndroid runtime (../../botwrecomp-src, local branch nebulos-local) as a Nebulos core.
# Differences from the project's own android/botwrecompandroid/app/src/main/cpp/CMakeLists.txt:
#  * SDL3 is linked STATICALLY (renamed Java package, recomp-common/build_sdl_static.sh) - no libSDL3.so / no libmain.so name clash;
#  * the library is named botw_main; recomp_vpad.c (Nebulos' SDL virtual gamepad) is added;
#  * FFmpeg (movies, start-screen background) is left out unless android/deps/out has it (HAVE_FFMPEG).
cmake_minimum_required(VERSION 3.22)
project(nebulos_botw C CXX ASM)

set(R ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../botwrecomp-src)   # the BOTWRecompAndroid repository
get_filename_component(R ${R} ABSOLUTE)
set(DEPS ${R}/android/deps/out)                        # android/deps/build_deps.sh (headers, volk, shaderc)
set(SDLS ${R}/android/deps/out-static)                 # recomp-common/build_sdl_static.sh
set(COMMON ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../nebulos/recomp-common/src/main/cpp)
get_filename_component(COMMON ${COMMON} ABSOLUTE)
if(NOT EXISTS ${SDLS}/lib/libSDL3.a)
    message(FATAL_ERROR "run recomp-common/build_sdl_static.sh first (needs android/deps/build_deps.sh)")
endif()
if(NOT BOTW_GEN_DIR)
    message(FATAL_ERROR "BOTW_GEN_DIR (generated smaa_hlsl.inc) is not set")
endif()

include(${R}/android/gamelib_id.cmake)
message(STATUS "GAMELIB_ID ${GAMELIB_ID}")

add_subdirectory(${R}/third_party/libadrenotools ${CMAKE_BINARY_DIR}/adrenotools)

add_library(SDL3 STATIC IMPORTED)
set_target_properties(SDL3 PROPERTIES IMPORTED_LOCATION ${SDLS}/lib/libSDL3.a)
add_library(shaderc_shared SHARED IMPORTED)
set_target_properties(shaderc_shared PROPERTIES IMPORTED_LOCATION ${DEPS}/lib/libshaderc_shared.so)

file(GLOB RT_SRC CONFIGURE_DEPENDS ${R}/runtime/*.c ${R}/runtime/hle/*.c ${R}/runtime/services/*.c ${R}/runtime/nvn/*.c
     ${R}/runtime/nvn/maxwell/*.c)
# FFmpeg 9.0.2 (movies, the start screen's background, TotK's Opus voices): ONE build shared by both Switch recomp cores, made by
# ../totkdroid-src/android/deps/build_ffmpeg_local.sh (a macOS-host copy of the project's build_ffmpeg.sh)
set(FFMPEG ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../totkdroid-src/android/deps/ffmpeg)
get_filename_component(FFMPEG ${FFMPEG} ABSOLUTE)
set(HAVE_FFMPEG FALSE)
if(EXISTS ${FFMPEG}/lib/libavformat.so)
    set(HAVE_FFMPEG TRUE)
    foreach(l avcodec avformat avutil swscale)
        add_library(${l} SHARED IMPORTED)
        set_target_properties(${l} PROPERTIES IMPORTED_LOCATION ${FFMPEG}/lib/lib${l}.so)
    endforeach()
else()
    list(REMOVE_ITEM RT_SRC ${R}/runtime/hle/movie.c)
endif()
file(GLOB VK_SRC CONFIGURE_DEPENDS ${R}/runtime/nvn/vulkan/*.c ${R}/runtime/nvn/vulkan/*.cpp)
file(GLOB UI_SRC CONFIGURE_DEPENDS ${R}/runtime/ui/*.mm)
set(IMGUI ${R}/third_party/imgui)
set(IMGUI_SRC ${IMGUI}/imgui.cpp ${IMGUI}/imgui_draw.cpp ${IMGUI}/imgui_tables.cpp ${IMGUI}/imgui_widgets.cpp
    ${IMGUI}/backends/imgui_impl_sdl3.cpp ${IMGUI}/backends/imgui_impl_vulkan.cpp)

add_library(botw_main SHARED ${RT_SRC} ${R}/runtime/platform/android.c ${VK_SRC} ${UI_SRC} ${IMGUI_SRC}
            ${DEPS}/volk/volk.c ${R}/android/common/guest_window.c ${R}/runtime/native_gate.S ${COMMON}/recomp_vpad.c)
set_source_files_properties(${UI_SRC} PROPERTIES LANGUAGE CXX COMPILE_OPTIONS "-xc++")
set_source_files_properties(${R}/runtime/gamelib.c PROPERTIES COMPILE_DEFINITIONS "GAMELIB_ID=\"${GAMELIB_ID}\"")
set_source_files_properties(${R}/runtime/gamebuild.c PROPERTIES COMPILE_DEFINITIONS "GAMELIB_ID=\"${GAMELIB_ID}\"")
add_custom_target(benchmark_commit BYPRODUCTS ${CMAKE_BINARY_DIR}/benchmark/benchmark_commit.h
    COMMAND ${CMAKE_COMMAND} -DR=${R} -DOUT=${CMAKE_BINARY_DIR}/benchmark/benchmark_commit.h -P ${R}/android/botwrecompandroid/app/src/main/cpp/benchmark_commit.cmake VERBATIM)
add_dependencies(botw_main benchmark_commit)
target_include_directories(botw_main PRIVATE ${CMAKE_BINARY_DIR}/benchmark)

target_include_directories(botw_main PRIVATE ${R}/runtime ${R}/runtime/nvn ${IMGUI} ${IMGUI}/backends ${SDLS}/include ${FFMPEG}/include ${DEPS}/include
                           ${DEPS}/volk ${BOTW_GEN_DIR} ${R}/third_party/smaa/Textures ${R}/android/common)
target_compile_definitions(botw_main PRIVATE
    VK_NO_PROTOTYPES VK_USE_PLATFORM_ANDROID_KHR IMGUI_IMPL_VULKAN_USE_VOLK
    VMA_STATIC_VULKAN_FUNCTIONS=0 VMA_DYNAMIC_VULKAN_FUNCTIONS=1)
if(HAVE_FFMPEG)
    target_link_libraries(botw_main PRIVATE avcodec avformat avutil swscale)
else()
    target_compile_definitions(botw_main PRIVATE LARECOMP_NO_FFMPEG)
endif()
target_compile_options(botw_main PRIVATE -O2 -g -fno-strict-aliasing -Wall -Wno-unused-function -Wno-format
    $<$<COMPILE_LANGUAGE:CXX>:-std=c++17>)
target_link_libraries(botw_main PRIVATE SDL3 shaderc_shared adrenotools android log dl m OpenSLES GLESv1_CM GLESv2)
# (runtime/hle.h, ipc.h, main.c: tables in named sections found through __start_ / __stop_ symbols)
# JNI_OnLoad lives in the statically linked SDL (registers the renamed SDLActivity natives): keep it exported
target_link_options(botw_main PRIVATE -Wl,-z,nostart-stop-gc -Wl,-u,JNI_OnLoad -Wl,-z,max-page-size=16384)
