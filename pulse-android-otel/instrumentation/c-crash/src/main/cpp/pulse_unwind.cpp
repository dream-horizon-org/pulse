#include "pulse_unwind.h"

#include <dlfcn.h>
#include <sys/mman.h>

#include <atomic>
#include <cerrno>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>

#include <cinttypes>

#include <unwindstack/DexFiles.h>
#include <unwindstack/Elf.h>
#include <unwindstack/MapInfo.h>
#include <unwindstack/Maps.h>
#include <unwindstack/MemoryLocal.h>
#include <unwindstack/Regs.h>
#include <unwindstack/RegsGetLocal.h>
#include <unwindstack/Unwinder.h>
#include <unwindstack/LocalUnwinder.h>

#include "utils/pulse_log.h"

namespace {

    std::mutex g_init_mutex;
    std::mutex g_reparse_mutex;
    bool g_inited = false;
    bool g_init_ok = false;

    /**
     * Shared process memory for all crash unwinders (DexFiles holds raw pointer into this).
     * Never swapped after first successful init.
     */
    std::shared_ptr<unwindstack::Memory> g_crash_process_memory;

    /** Current crash unwinder; replaced on reparse (Java thread). Signal handler loads with acquire. */
    std::atomic<unwindstack::Unwinder *> g_crash_unwinder{nullptr};

    /** Kept for process lifetime: Unwinder stores raw DexFiles* from SetDexFiles. */
    std::unique_ptr<unwindstack::DexFiles> g_crash_dex_files;

    std::atomic_bool g_unwinding{false};

    void pulse_strncpy(char *dst, const char *src, size_t n) __asyncsafe {
        if (dst == nullptr || n == 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "pulse_strncpy failed as dst is null or n 0");
            return;
        }
        dst[0] = '\0';
        if (src == nullptr) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "pulse_strncpy: src is null");
            return;
        }
        std::strncpy(dst, src, n - 1);
        dst[n - 1] = '\0';
    }

    void pulse_hex_encode(char *dst, const void *src, size_t byte_count, size_t max_chars) __asyncsafe {
        static const char *hex = "0123456789abcdef";
        if (dst == nullptr || max_chars == 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "pulse_hex_encode failed as dst is null or max_chars is 0");
            return;
        }
        if (src == nullptr) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "pulse_hex_encode: src is null");
            dst[0] = '\0';
            return;
        }
        if (byte_count == 0) {
            dst[0] = '\0';
            return;
        }
        const auto *bytes = static_cast<const unsigned char *>(src);
        const size_t byte_copy_count =
                (max_chars > byte_count * 2) ? byte_count : (max_chars - 1) / 2;
        char *out = dst;
        for (size_t i = 0; i < byte_copy_count; ++i) {
            *out++ = hex[(bytes[i] >> 4) & 0xF];
            *out++ = hex[bytes[i] & 0xF];
        }
        *out = '\0';
    }

    bool check_invalid_libname(const char *filename) __asyncsafe {
        if (filename == nullptr || filename[0] == '\0') {
            return true;
        }
        const size_t length = std::strlen(filename);
        return length >= 4 && filename[length - 4] == '.' && filename[length - 3] == 'a' &&
                filename[length - 2] == 'p' && filename[length - 1] == 'k';
    }

    void fallback_symbols(uint64_t addr, PulseNativeStackFrame *dst) __asyncsafe {
        Dl_info info{};
        if (dladdr(reinterpret_cast<void *>(static_cast<uintptr_t>(addr)), &info) == 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "fallback_symbols failed");
            return;
        }
        if (info.dli_fname != nullptr) {
            pulse_strncpy(dst->filename, info.dli_fname, sizeof(dst->filename));
        }
        if (info.dli_sname != nullptr) {
            pulse_strncpy(dst->method, info.dli_sname, sizeof(dst->method));
        }
    }

    void populate_code_identifier(unwindstack::Unwinder *uw, const unwindstack::FrameData &frame,
            PulseNativeStackFrame *dst) __asyncsafe {
        if (uw == nullptr) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "populate_code_identifier: unwinder is null");
            return;
        }
        unwindstack::Maps *maps = uw->GetMaps();
        if (maps == nullptr) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "populate_code_identifier: GetMaps returned null");
            return;
        }
        unwindstack::MapInfo *map_info = maps->Find(frame.pc);
        if (map_info == nullptr) {
            return;
        }
        auto *elf_fields = map_info->elf_fields();
        if (elf_fields == nullptr) {
            return;
        }
        unwindstack::SharedString *shared_build_id = elf_fields->build_id_.load();
        if (shared_build_id == nullptr || shared_build_id->empty()) {
            // build_id_ is populated lazily via MapInfo::GetPrintableBuildID().
            // It is null when the crash occurs before anyone called that path for
            // this map — common on early-startup crashes or freshly dlopen'd libraries.
            // After Unwind() completes the Elf object exists, so we can read the
            // build ID directly from it (heap alloc; not formally async-signal-safe
            // but safe in practice after Unwind() has already parsed the ELF).
            unwindstack::Elf *elf = elf_fields->elf_.get();
            if (elf == nullptr) {
                pulse_logd(PULSE_LOG_TAG_CCRASH,
                        "populate_code_identifier: build_id_ null and elf_ null pc=0x%" PRIx64,
                        static_cast<uint64_t>(frame.pc));
                return;
            }
            const std::string elf_build_id = elf->GetBuildID();
            if (elf_build_id.empty()) {
                pulse_logd(PULSE_LOG_TAG_CCRASH,
                        "populate_code_identifier: ELF fallback empty build_id pc=0x%" PRIx64,
                        static_cast<uint64_t>(frame.pc));
                return;
            }
            pulse_logd(PULSE_LOG_TAG_CCRASH,
                    "populate_code_identifier: used ELF fallback for build_id pc=0x%" PRIx64,
                    static_cast<uint64_t>(frame.pc));
            pulse_hex_encode(dst->code_identifier, elf_build_id.data(), elf_build_id.length(),
                    sizeof(dst->code_identifier));
            return;
        }
        const auto &build_id = static_cast<const std::string &>(*shared_build_id);
        pulse_hex_encode(dst->code_identifier, build_id.data(), build_id.length(), sizeof(dst->code_identifier));
    }

}  // namespace

bool pulse_unwind_init() {
    std::lock_guard<std::mutex> lock(g_init_mutex);
    if (g_inited) {
        return g_init_ok;
    }
    g_inited = true;

    g_crash_process_memory = std::make_shared<unwindstack::MemoryLocal>();

    auto *maps = new unwindstack::LocalUpdatableMaps();
    if (!maps->Parse()) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "pulse_unwind_init: LocalUpdatableMaps::Parse failed errno=%d", errno);
        g_init_ok = false;
        delete maps;
        g_crash_process_memory.reset();
        return false;
    }

    const auto arch = unwindstack::Regs::CurrentArch();
    g_crash_dex_files = unwindstack::CreateDexFiles(arch, g_crash_process_memory);

    auto *unwinder = new unwindstack::Unwinder(
            kPulseUnwindFramesMax, maps, g_crash_process_memory);
    unwinder->SetDexFiles(g_crash_dex_files.get());
    g_crash_unwinder.store(unwinder, std::memory_order_release);

    auto current_time_unwinder = new unwindstack::LocalUnwinder();
    if (!current_time_unwinder->Init()) {
        delete current_time_unwinder;
        current_time_unwinder = nullptr;
    }

    g_init_ok = true;
    return true;
}

const char *pulse_binary_arch() __asyncsafe {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__i386__)
    return "x86";
#elif defined(__x86_64__)
    return "x86_64";
#else
    return "unknown";
#endif
}

size_t pulse_unwind_crash_stack(PulseNativeStackFrame *out_frames, size_t max_frames, void *ucontext) __asyncsafe {
    if (out_frames == nullptr || max_frames == 0) {
        pulse_loge(PULSE_LOG_TAG_CCRASH,
                "pulse_unwind_crash_stack: invalid args out_frames=%p max_frames=%zu",
                static_cast<void *>(out_frames),
                max_frames);
        return 0;
    }
    if (!g_init_ok) {
        pulse_loge(PULSE_LOG_TAG_CCRASH,
                "pulse_unwind_crash_stack: unwinder not ready g_init_ok=%d",
                static_cast<int>(g_init_ok));
        return 0;
    }

    bool expected = false;
    if (!g_unwinding.compare_exchange_strong(expected, true)) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "pulse_unwind_crash_stack: reentrant unwind skipped");
        return 0;
    }

    unwindstack::Unwinder *const local_unwinder = g_crash_unwinder.load(std::memory_order_acquire);
    if (local_unwinder == nullptr) {
        g_unwinding = false;
        return 0;
    }

    std::unique_ptr<unwindstack::Regs> owned_regs;
    if (ucontext != nullptr) {
        owned_regs.reset(unwindstack::Regs::CreateFromUcontext(unwindstack::Regs::CurrentArch(), ucontext));
    } else {
        owned_regs.reset(unwindstack::Regs::CreateFromLocal());
        unwindstack::RegsGetLocal(owned_regs.get());
    }
    // Do not call fallback_pc() here: Unwinder::Unwind already strips PAC when Find(pc) is null.
    // Stripping unconditionally can move a valid fault PC into an anonymous map (empty name) and
    // yield ERROR_INVALID_ELF / single-frame stacks.
    local_unwinder->SetRegs(owned_regs.get());

    {
        // If the fault map lacks PROT_EXEC it is a stale linker reservation that has since been
        // replaced by the real .so segments. Reparse the maps in-place before Unwind() so that
        // Find(pc) returns the correct file-backed MapInfo.  LocalUpdatableMaps::Reparse() is
        // designed for this use (called from signal context by Android profiling tools).
        unwindstack::Maps *const fault_maps = local_unwinder->GetMaps();
        if (fault_maps != nullptr) {
             unwindstack::MapInfo *const mi = fault_maps->Find(owned_regs->pc());
             if (mi != nullptr && !(static_cast<uint32_t>(mi->flags()) & static_cast<uint32_t>(PROT_EXEC))) {
                 auto *lum = dynamic_cast<unwindstack::LocalUpdatableMaps *>(fault_maps);
                 lum->Reparse(nullptr);
             }
        }
    }

    local_unwinder->Unwind();

    size_t n = 0;
    for (const auto &frame: local_unwinder->frames()) {
        if (n >= max_frames) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "pulse_unwind_crash_stack: reached max_frames=%zu breaking",
                    max_frames);
            break;
        }
        PulseNativeStackFrame &dst = out_frames[n];
        std::memset(&dst, 0, sizeof(dst));

        dst.frame_address = frame.pc;
        dst.rel_pc = frame.rel_pc;
        dst.symbol_offset = frame.function_offset;
        dst.symbol_address = frame.pc - frame.function_offset;

        const uint64_t offset = frame.map_load_bias + (frame.map_exact_offset - frame.map_elf_start_offset);
        if (offset < frame.map_start) {
            dst.load_address = frame.map_start - offset;
        } else {
            dst.load_address = frame.map_start - (frame.map_exact_offset - frame.map_elf_start_offset);
        }

        populate_code_identifier(local_unwinder, frame, &dst);

        const char *map_name = frame.map_name.c_str();
        if (check_invalid_libname(map_name) || frame.function_name.empty()) {
            fallback_symbols(frame.pc, &dst);
        } else {
            pulse_strncpy(dst.filename, map_name, sizeof(dst.filename));
            pulse_strncpy(dst.method, frame.function_name.c_str(), sizeof(dst.method));
        }
        n++;
    }

    g_unwinding = false;
    return n;
}

bool pulse_unwind_reparse_maps() {
    if (!g_init_ok || g_crash_process_memory == nullptr) {
        return false;
    }
    std::lock_guard<std::mutex> lock(g_reparse_mutex);

    auto *new_maps = new unwindstack::LocalUpdatableMaps();
    if (!new_maps->Parse()) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "pulse_unwind_reparse_maps: Parse failed errno=%d", errno);
        delete new_maps;
        return false;
    }

    auto *new_unwinder = new unwindstack::Unwinder(
            kPulseUnwindFramesMax, new_maps, g_crash_process_memory);
    new_unwinder->SetDexFiles(g_crash_dex_files.get());

    unwindstack::Unwinder *const prev = g_crash_unwinder.exchange(new_unwinder, std::memory_order_acq_rel);
    (void) prev;
    // Intentionally leak `prev` (and its Maps): it may still be referenced by an in-flight
    // signal unwind; safe free would need epoch/quiescence. Few reparses per process.

    return true;
}
