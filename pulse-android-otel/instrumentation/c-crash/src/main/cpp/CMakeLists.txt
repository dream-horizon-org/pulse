cmake_minimum_required(VERSION 3.22.1)

project(pulse_c_crash)

add_library(
    pulse_c_crash
    SHARED
    pulse_c_crash.cpp
    pulse_unwind.cpp
)

target_compile_options(pulse_c_crash PRIVATE -Wall -Wextra -Werror)

find_library(log-lib log)

target_link_libraries(
    pulse_c_crash
    ${log-lib}
)

# 16KB page-size devices (Play / Android 15+).
target_link_options(
    pulse_c_crash
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
