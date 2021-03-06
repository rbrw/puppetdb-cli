# Defines how cmake should behave, and the minimum version necessary to build.
cmake_minimum_required(VERSION 3.2.2)

# Project Setup - modify to match project naming
## Source code for a simple command-line executable for a dynamic library will be generated from the project name.
## The command-line and library names will be based off the project name.
project(puppetdb-cli VERSION 0.1.0)

string(MAKE_C_IDENTIFIER ${PROJECT_NAME} PROJECT_C_NAME)
string(TOUPPER ${PROJECT_C_NAME} PROJECT_NAME_UPPER)
string(TOLOWER ${PROJECT_C_NAME} PROJECT_NAME_LOWER)

# Common cmake setup
if (NOT CMAKE_BUILD_TYPE)
    message(STATUS "Defaulting to a release build.")
    set(CMAKE_BUILD_TYPE "Release" CACHE STRING "Choose the type of build, options are: None Debug Release RelWithDebInfo MinSizeRel." FORCE)
endif()

enable_testing()

# Leatherman setup
list(APPEND CMAKE_MODULE_PATH "${PROJECT_SOURCE_DIR}/vendor/leatherman/cmake")

## Before we find any packages, we want to pull in the common leatherman options, as they can affect commonly-used packages.
include(options)
## Pull in common cflags setting from leatherman. Don't override CMAKE_CXX_FLAGS at the project root to avoid impacting 3rd party code.
include(cflags)
set(${PROJECT_NAME_UPPER}_CXX_FLAGS "${LEATHERMAN_CXX_FLAGS}")
add_definitions(${LEATHERMAN_DEFINITIONS})
## Pull in helper macros for working with leatherman libraries
include(leatherman)

# Add other dependencies
find_package(Boost 1.54 REQUIRED COMPONENTS program_options filesystem)
find_package(CURL REQUIRED)

if (CURL_STATIC)
    add_definitions(-DCURL_STATICLIB)
    if (WIN32)
      # Linking statically on Windows requires some extra libraries.
      set(CURL_DEPS wldap32.lib ws2_32.lib)
    endif()
endif()

list(APPEND CURL_LIBRARIES ${CURL_DEPS})
export_var(CURL_LIBRARIES)

# Display a summary of the features
include(FeatureSummary)
feature_summary(WHAT ALL)

# Add build directories, both Leatherman and the project's source code
set(LEATHERMAN_USE_LOCALE TRUE)
set(LEATHERMAN_USE_CATCH TRUE)
set(LEATHERMAN_USE_NOWIDE TRUE)
set(LEATHERMAN_USE_LOGGING TRUE)
set(LEATHERMAN_USE_UTIL TRUE)
set(LEATHERMAN_USE_FILE_UTIL TRUE)
set(LEATHERMAN_USE_RAPIDJSON TRUE)
set(LEATHERMAN_USE_JSON_CONTAINER TRUE)
add_subdirectory("vendor/leatherman")

add_subdirectory(lib)
add_subdirectory(exe)

# Add cpplint and cppcheck targets
file(GLOB_RECURSE ALL_SOURCES lib/src/*.cc lib/inc/*.hpp exe/*.cc)
add_cpplint_files(${ALL_SOURCES})
enable_cpplint()

add_cppcheck_dirs("${PROJECT_SOURCE_DIR}/lib" "${PROJECT_SOURCE_DIR}/exe")
enable_cppcheck()

