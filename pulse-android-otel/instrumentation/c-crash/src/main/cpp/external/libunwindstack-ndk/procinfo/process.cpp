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

#include <procinfo/process.h>

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <string>

#include <android-base/unique_fd.h>

using android::base::unique_fd;

namespace android {
namespace procinfo {

static ProcessState parse_state(char state) {
  switch (state) {
    case 'R':
      return kProcessStateRunning;
    case 'S':
      return kProcessStateSleeping;
    case 'D':
      return kProcessStateUninterruptibleWait;
    case 'T':
      return kProcessStateStopped;
    case 'Z':
      return kProcessStateZombie;
    default:
      return kProcessStateUnknown;
  }
}
} /* namespace procinfo */
} /* namespace android */
