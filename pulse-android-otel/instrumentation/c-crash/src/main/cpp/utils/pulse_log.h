#pragma once

#include "build.h"

#ifdef __cplusplus
extern "C" {
#endif

/** Default Android log tag for c-crash native code. */
#define PULSE_LOG_TAG_CCRASH "PulseSDK:CCrashCpp"

/** Formatted Android logcat DEBUG (see `android/log.h`). */
void pulse_logd(const char *tag, const char *fmt, ...) __asyncsafe;

/** Formatted Android logcat ERROR. */
void pulse_loge(const char *tag, const char *fmt, ...) __asyncsafe;

#ifdef __cplusplus
}
#endif
