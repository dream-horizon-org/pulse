#include "utils/pulse_log.h"

#include <android/log.h>

#include <cstdarg>

void pulse_logd(const char *tag, const char *fmt, ...) __asyncsafe {
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(ANDROID_LOG_DEBUG, tag, fmt, ap);
    va_end(ap);
}

void pulse_loge(const char *tag, const char *fmt, ...) __asyncsafe {
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(ANDROID_LOG_ERROR, tag, fmt, ap);
    va_end(ap);
}
