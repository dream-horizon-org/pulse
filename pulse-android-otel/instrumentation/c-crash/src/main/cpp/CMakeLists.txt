cmake_minimum_required(VERSION 3.22.1)

project(pulse_c_crash)

add_subdirectory(external/libunwindstack-ndk/cmake)

add_library(
    pulse_c_crash
    SHARED
    utils/pulse_log.cpp
    pulse_c_crash.cpp
    pulse_unwind.cpp
)

target_include_directories(pulse_c_crash PRIVATE ${CMAKE_CURRENT_LIST_DIR})

target_compile_options(pulse_c_crash PRIVATE -Wall -Wextra -Werror)

find_library(log-lib log)

target_link_libraries(
    pulse_c_crash
    PRIVATE
    unwindstack
    ${log-lib}
)

# 16KB page-size devices (Play / Android 15+).
target_link_options(
    pulse_c_crash
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
