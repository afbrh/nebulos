# libsupermetroid_main.so: the snesrev/sm decompilation (../../../../../sm-snes-src, local branch nebulos-local, on top of nickdesaulniers/sm) + SDL2
# linked in statically (the renamed copy made by sm-snes-src/nebulos/rename_sdl.sh). One library: SDL's JNI functions and the game's SDL_main live together.
cmake_minimum_required(VERSION 3.22)
project(supermetroid_nebulos C CXX)

get_filename_component(PORT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../sm-snes-src" ABSOLUTE)

set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
set(SDL2_DISABLE_INSTALL ON CACHE BOOL "" FORCE)
add_subdirectory("${PORT}/nebulos/deps/SDL2" sdl2 EXCLUDE_FROM_ALL)

file(GLOB GAME_SOURCES "${PORT}/src/*.c" "${PORT}/src/snes/*.c")
add_library(supermetroid_main SHARED
  ${GAME_SOURCES}
  "${PORT}/third_party/gl_core/gl_core_3_1.c"
  "${PORT}/nebulos/nebulos_bridge.c")
set_target_properties(supermetroid_main PROPERTIES LINKER_LANGUAGE CXX)   # SDL's Android controller code is C++
target_include_directories(supermetroid_main PRIVATE "${PORT}" "${PORT}/src")
target_compile_definitions(supermetroid_main PRIVATE NEBULOS SYSTEM_VOLUME_MIXER_AVAILABLE=0)
# the game's code relies on signed wrap-around and type punning, as the original machine code did (-O2 -fno-strict-aliasing: as its tests are built)
target_compile_options(supermetroid_main PRIVATE -O2 -fno-strict-aliasing -fwrapv -Wno-parentheses)
target_link_libraries(supermetroid_main PRIVATE -Wl,--whole-archive SDL2-static -Wl,--no-whole-archive android log m)
target_link_options(supermetroid_main PRIVATE -Wl,-z,max-page-size=16384)
