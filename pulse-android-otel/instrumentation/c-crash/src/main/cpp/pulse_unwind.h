#pragma once

#include <cstddef>
#include <cstdint>

namespace pulse {

struct UnwindFrame {
  uintptr_t pc;
};

size_t unwind_current_thread(UnwindFrame *out_frames, size_t max_frames, size_t skip_frames);

}  // namespace pulse

