cmake_minimum_required(VERSION 3.22)

project(sdlpop_android C CXX)

set(PROJECT_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../..")
set(SDLPOP_ROOT "${PROJECT_ROOT}/external/SDLPoP")
set(SDLPOP_SRC "${SDLPOP_ROOT}/src")
set(SDL2_ROOT "${PROJECT_ROOT}/external/SDL2")
set(SDL2_IMAGE_ROOT "${PROJECT_ROOT}/external/SDL2_image")

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory("${SDL2_ROOT}" "${CMAKE_BINARY_DIR}/SDL2")

set(SDL2IMAGE_INSTALL OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_SAMPLES OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_TESTS OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_DEPS_SHARED OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_VENDORED OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_BACKEND_STB ON CACHE BOOL "" FORCE)
set(SDL2IMAGE_AVIF OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_GIF OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_JXL OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_LBM OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_PCX OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_PNM OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_QOI OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_SVG OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_TGA OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_TIF OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_WEBP OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_XCF OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_XPM OFF CACHE BOOL "" FORCE)
set(SDL2IMAGE_XV OFF CACHE BOOL "" FORCE)
add_subdirectory("${SDL2_IMAGE_ROOT}" "${CMAKE_BINARY_DIR}/SDL2_image")

set(SDLPOP_SOURCES
        "${CMAKE_CURRENT_LIST_DIR}/android_sdlpop_main.c"
        "${SDLPOP_SRC}/data.c"
        "${SDLPOP_SRC}/seg000.c"
        "${SDLPOP_SRC}/seg001.c"
        "${SDLPOP_SRC}/seg002.c"
        "${SDLPOP_SRC}/seg003.c"
        "${SDLPOP_SRC}/seg004.c"
        "${SDLPOP_SRC}/seg005.c"
        "${SDLPOP_SRC}/seg006.c"
        "${SDLPOP_SRC}/seg007.c"
        "${SDLPOP_SRC}/seg008.c"
        "${SDLPOP_SRC}/seg009.c"
        "${SDLPOP_SRC}/seqtbl.c"
        "${SDLPOP_SRC}/options.c"
        "${SDLPOP_SRC}/midi.c"
        "${SDLPOP_SRC}/opl3.c"
        "${SDLPOP_SRC}/replay.c"
        "${SDLPOP_SRC}/lighting.c"
        "${SDLPOP_SRC}/screenshot.c"
        "${SDLPOP_SRC}/menu.c"
        "${SDLPOP_SRC}/stb_vorbis.c")

add_library(main SHARED ${SDLPOP_SOURCES})

target_include_directories(main PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/include"
        "${SDLPOP_SRC}"
        "${SDL2_ROOT}/include"
        "${SDL2_IMAGE_ROOT}/include")

target_compile_features(main PRIVATE c_std_99)
target_compile_definitions(main PRIVATE SDLPOP_ANDROID_TOUCH_ONLY=1)
target_compile_options(main PRIVATE
        -Wall
        -Wno-unused-variable
        -Wno-unused-function
        -Wno-sign-compare)

target_link_libraries(main PRIVATE
        SDL2::SDL2
        SDL2_image::SDL2_image
        m
        log)
