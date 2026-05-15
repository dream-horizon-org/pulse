/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>

#include <string>

#define LOG_TAG "unwind"

#include <android/log.h>

#include <android-base/stringprintf.h>

#include <unwindstack/Log.h>

namespace unwindstack {

    static bool g_print_to_stdout = false;

    void log_to_stdout(bool enable) {
        g_print_to_stdout = enable;
    }

// Send the data to the log.
    void log(uint8_t indent, const char *format, ...) {
    }

    void log_async_safe(const char *, ...) {
    }

}  // namespace unwindstack
