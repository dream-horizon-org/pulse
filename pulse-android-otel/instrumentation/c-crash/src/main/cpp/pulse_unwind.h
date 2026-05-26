#pragma once

#include <cstddef>
#include <cstdint>

#include "utils/build.h"

/** Max native frames captured for a crash report. */
constexpr size_t kPulseUnwindFramesMax = 192;

/**
 * One stack frame from libunwindstack (crash-time unwind).
 * String fields are NUL-terminated; empty if unknown.
 */
struct PulseNativeStackFrame {
    uint64_t frame_address;
    uint64_t rel_pc;
    uint64_t load_address;
    uint64_t symbol_address;
    uint64_t symbol_offset;
    char code_identifier[128];
    char filename[512];
    char method[256];
};

/**
 * One-time init (safe from JNI / normal threads — not async-signal-safe).
 * Parses /proc maps and builds the crash unwinder + DEX support.
 * @return true if unwinder is ready
 */
bool pulse_unwind_init();

/**
 * Re-merge /proc/self/maps into the crash unwinder (e.g. after System.loadLibrary of app JNI code).
 * Not async-signal-safe; call from a normal thread only.
 */
bool pulse_unwind_reparse_maps();

/** Android ABI of this process (compile-time), e.g. {@code arm64-v8a}. Async-signal-safe. */
const char *pulse_binary_arch() __asyncsafe;

/**
 * Unwind from crash context. Prefer non-null @p ucontext from the signal handler.
 * Async-signal-safe aside from libunwindstack internal behavior.
 * @return number of frames written to @p out_frames (at most @p max_frames)
 */
size_t pulse_unwind_crash_stack(PulseNativeStackFrame *out_frames, size_t max_frames, void *ucontext) __asyncsafe;
