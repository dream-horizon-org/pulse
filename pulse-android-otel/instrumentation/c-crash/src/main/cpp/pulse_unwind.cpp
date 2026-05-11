#include "pulse_unwind.h"

#include <unwind.h>

namespace pulse {

struct UnwindState {
  UnwindFrame *frames;
  size_t max_frames;
  size_t count;
  size_t skip;
};

static _Unwind_Reason_Code unwind_cb(struct _Unwind_Context *ctx, void *arg) {
  auto *state = static_cast<UnwindState *>(arg);
  if (state->skip > 0) {
    state->skip--;
    return _URC_NO_REASON;
  }
  if (state->count >= state->max_frames) {
    return _URC_END_OF_STACK;
  }
  uintptr_t pc = _Unwind_GetIP(ctx);
  state->frames[state->count].pc = pc;
  state->count++;
  return _URC_NO_REASON;
}

size_t unwind_current_thread(UnwindFrame *out_frames, size_t max_frames, size_t skip_frames) {
  if (out_frames == nullptr || max_frames == 0) {
    return 0;
  }
  UnwindState state{out_frames, max_frames, 0, skip_frames};
  _Unwind_Backtrace(unwind_cb, &state);
  return state.count;
}

}  // namespace pulse

