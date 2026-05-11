cmake_minimum_required(VERSION 3.22.1)

project(demo_native_crash)

add_library(
    demo_native_crash
    SHARED
    demo_native_crash.cpp
)

find_library(log-lib log)

target_compile_options(demo_native_crash PRIVATE -Wall -Wextra -Werror)

target_link_libraries(
    demo_native_crash
    ${log-lib}
)

# 16KB page-size devices (Play / Android 15+).
target_link_options(
    demo_native_crash
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
