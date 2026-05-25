#include <cerrno>
#include <fcntl.h>
#include <cinttypes>
#include <jni.h>
#include <csignal>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <ctime>
#include <unistd.h>

#include "utils/pulse_log.h"
#include "pulse_unwind.h"

namespace {

    constexpr int kSignals[] = {SIGSEGV, SIGABRT, SIGILL, SIGFPE, SIGBUS, SIGTRAP};
    constexpr size_t kSignalCount = sizeof(kSignals) / sizeof(kSignals[0]);

    struct sigaction g_old_actions[kSignalCount];

    char g_reports_dir[512];
    char g_metadata_source_path[512];
    char g_crash_file_name[256];
    char g_metadata_file_name[256];
    char g_session_id[128];
    volatile sig_atomic_t g_installed = 0;

    /** Alternate signal stack for SA_ONSTACK (parity with bugsnag signal_handler.c). */
    stack_t g_alt_stack{};
    void *g_alt_stack_sp = nullptr;

    /**
     * Crash-time report buffers must NOT live on the signal altstack: with SA_ONSTACK the handler
     * stack is small (~8–32 KiB). Large locals (64 KiB JSON + unwind frames) overflow before any
     * line in write_report runs. Single crash path — not reentrant with nested native crashes.
     */
    constexpr size_t kWriteReportJsonCap = 65536;

    alignas(64) char g_write_report_json[kWriteReportJsonCap];
    PulseNativeStackFrame g_write_report_raw_frames[kPulseUnwindFramesMax];
    char g_write_report_thread_name[32]; // /proc/comm is max 15 chars + NUL

    constexpr size_t kCopyBufferSize = 4096;
    alignas(64) char g_copy_buffer[kCopyBufferSize];

    pid_t get_tid() __asyncsafe {
        return static_cast<pid_t>(syscall(SYS_gettid));
    }

    const char *signal_name(int sig) __asyncsafe {
        switch (sig) {
            case SIGSEGV: return "SIGSEGV";
            case SIGABRT: return "SIGABRT";
            case SIGILL:  return "SIGILL";
            case SIGFPE:  return "SIGFPE";
            case SIGBUS:  return "SIGBUS";
            case SIGTRAP: return "SIGTRAP";
            default:      return "SIG?";
        }
    }

    void safe_write_all(int fd, const char *buf, size_t len) __asyncsafe {
        size_t off = 0;
        while (off < len) {
            ssize_t rc = write(fd, buf + off, len - off);
            if (rc <= 0) {
                pulse_loge(PULSE_LOG_TAG_CCRASH,
                        "safe_write_all: write failed fd=%d off=%zu len=%zu rc=%zd errno=%d",
                        fd, off, len, rc, errno);
                break;
            }
            off += static_cast<size_t>(rc);
        }
        pulse_logd(PULSE_LOG_TAG_CCRASH, "safe_write_all: done");
    }

    void copy_file_asyncsafe(const char *src_path, const char *dest_path) __asyncsafe {
        if (src_path == nullptr || src_path[0] == '\0') {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "copy_file_asyncsafe: src path empty");
            return;
        }
        if (dest_path == nullptr || dest_path[0] == '\0') {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "copy_file_asyncsafe: dest path empty");
            return;
        }

        const int src_fd = open(src_path, O_RDONLY | O_CLOEXEC);
        if (src_fd < 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "copy_file_asyncsafe: open src failed path=%s errno=%d", src_path, errno);
            return;
        }

        const int dest_fd = open(dest_path, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
        if (dest_fd < 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "copy_file_asyncsafe: open dest failed path=%s errno=%d", dest_path, errno);
            close(src_fd);
            return;
        }

        for (;;) {
            const ssize_t n = read(src_fd, g_copy_buffer, kCopyBufferSize);
            if (n < 0) {
                pulse_loge(PULSE_LOG_TAG_CCRASH,
                        "copy_file_asyncsafe: read failed src=%s errno=%d", src_path, errno);
                break;
            }
            if (n == 0) {
                break;
            }
            safe_write_all(dest_fd, g_copy_buffer, static_cast<size_t>(n));
        }

        close(src_fd);
        close(dest_fd);
    }

    /**
     * Append a JSON-escaped string value (including surrounding quotes) to buf[*pos].
     * Properly escapes \n \r \t; replaces other control chars with '?'.
     * Writes JSON null if val is null or empty.
     */
    void append_json_escaped_string(char *buf, size_t buf_cap, size_t *pos, const char *val) __asyncsafe {
        if (*pos >= buf_cap - 8) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "append_json_escaped_string: buffer exhausted pos=%zu cap=%zu",
                    *pos,
                    buf_cap);
            return;
        }
        if (val == nullptr || val[0] == '\0') {
            *pos += static_cast<size_t>(snprintf(buf + *pos, buf_cap - *pos, "null"));
            return;
        }
        if (*pos >= buf_cap - 1) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "append_json_escaped_string: no room for opening quote pos=%zu cap=%zu",
                    *pos,
                    buf_cap);
            return;
        }
        buf[(*pos)++] = '"';
        for (const auto *p = reinterpret_cast<const unsigned char *>(val); *p != 0 && *pos + 6 < buf_cap; ++p) {
            if (*p == '"' || *p == '\\') {
                buf[(*pos)++] = '\\';
                buf[(*pos)++] = static_cast<char>(*p);
            } else if (*p == '\n') {
                buf[(*pos)++] = '\\'; buf[(*pos)++] = 'n';
            } else if (*p == '\r') {
                buf[(*pos)++] = '\\'; buf[(*pos)++] = 'r';
            } else if (*p == '\t') {
                buf[(*pos)++] = '\\'; buf[(*pos)++] = 't';
            } else if (*p < 0x20U) {
                buf[(*pos)++] = '?';
            } else {
                buf[(*pos)++] = static_cast<char>(*p);
            }
        }
        if (*pos < buf_cap) buf[(*pos)++] = '"';
    }

    /**
     * Read thread name from /proc/self/task/<tid>/comm into buf.
     * Strips trailing newline. Writes empty string on failure.
     * Async-signal-safe: uses only open/read/close.
     */
    void read_thread_name(pid_t tid, char *buf, size_t buf_cap) __asyncsafe {
        char path[64];
        snprintf(path, sizeof(path), "/proc/self/task/%d/comm", static_cast<int>(tid));
        const int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) { buf[0] = '\0'; return; }
        const ssize_t n = read(fd, buf, buf_cap - 1);
        close(fd);
        if (n <= 0) { buf[0] = '\0'; return; }
        buf[n] = '\0';
        if (n > 0 && buf[n - 1] == '\n') buf[n - 1] = '\0';
    }

    bool ensure_dir(const char *path) __asyncsafe {
        if (path == nullptr || path[0] == '\0') {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "ensure_dir: path null or empty");
            return false;
        }
        if (mkdir(path, 0700) == 0) return true;
        if (errno == EEXIST) return true;
        pulse_loge(PULSE_LOG_TAG_CCRASH, "ensure_dir: mkdir failed path=%s errno=%d", path, errno);
        return false;
    }

    void write_report(int sig, siginfo_t *info, void *ucontext) __asyncsafe {
        if (g_reports_dir[0] == '\0') {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "write_report: global reports dir is empty");
            return;
        }

        if (g_crash_file_name[0] == '\0') {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "write_report: crash file name is empty");
            return;
        }

        timespec ts{};
        clock_gettime(CLOCK_REALTIME, &ts);
        const long long ts_ms = static_cast<long long>(ts.tv_sec) * 1000LL + ts.tv_nsec / 1000000LL;
        const char *session_id = g_session_id[0] != '\0' ? g_session_id : "unknown";

        char path[768];
        const int pid = static_cast<int>(getpid());
        const pid_t tid_raw = get_tid();
        const int tid = static_cast<int>(tid_raw);

        snprintf(path, sizeof(path), "%s/%lld_%s", g_reports_dir, ts_ms, session_id);
        if (!ensure_dir(path)) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "write_report: failed to create crash dir path=%s", path);
            return;
        }

        snprintf(path, sizeof(path), "%s/%lld_%s/%s",
                g_reports_dir, ts_ms, session_id, g_crash_file_name);

        read_thread_name(tid_raw, g_write_report_thread_name, sizeof(g_write_report_thread_name));

        pulse_logd(PULSE_LOG_TAG_CCRASH, "write_report: opening path = %s", path);
        int fd = open(path, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
        if (fd == -1) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "write_report: failed to open path = %s", path);
            return;
        }

        PulseUnwindCrashDiag unwind_diag{};
        const size_t frame_count = pulse_unwind_crash_stack(
                g_write_report_raw_frames, kPulseUnwindFramesMax, ucontext, &unwind_diag);
        if (frame_count == 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "write_report: unwind returned 0 frames sig=%d ucontext=%p",
                    sig, ucontext);
        }

        char *const json = g_write_report_json;
        size_t pos = 0;

        const uintptr_t fault_addr = info ? reinterpret_cast<uintptr_t>(info->si_addr) : 0;

        // Header: ts, pid, tid, thread_name
        pos += static_cast<size_t>(snprintf(json + pos, kWriteReportJsonCap - pos,
                "{"
                "\"ts_ms\":%lld,"
                "\"pid\":%d,"
                "\"tid\":%d,"
                "\"thread_name\":",
                ts_ms, pid, tid));
        append_json_escaped_string(json, kWriteReportJsonCap, &pos, g_write_report_thread_name);

        // Signal info + unwind diagnostics
        pos += static_cast<size_t>(snprintf(json + pos, kWriteReportJsonCap - pos,
                ",\"signal\":%d,"
                "\"signal_name\":\"%s\","
                "\"fault_addr\":\"0x%lx\","
                "\"_pulse_unwind_diag\":{"
                "\"reg_pc\":\"0x%" PRIx64 "\","
                "\"reg_sp\":\"0x%" PRIx64 "\","
                "\"raw_num_frames\":%zu,"
                "\"last_error\":%u,"
                "\"warnings\":%" PRIu64 ","
                "\"ucontext\":%u"
                "},"
                "\"stack_frames\":[",
                sig, signal_name(sig),
                static_cast<unsigned long>(fault_addr),
                static_cast<uint64_t>(unwind_diag.reg_pc),
                static_cast<uint64_t>(unwind_diag.reg_sp),
                unwind_diag.raw_num_frames,
                static_cast<unsigned>(unwind_diag.last_error),
                static_cast<uint64_t>(unwind_diag.warnings),
                static_cast<unsigned>(unwind_diag.ucontext_present)));

        // Raw frame objects (structured data for server-side re-symbolication)
        for (size_t i = 0; i < frame_count; i++) {
            if (pos + 256 >= kWriteReportJsonCap) {
                pulse_loge(PULSE_LOG_TAG_CCRASH,
                        "write_report: JSON buffer full writing stack_frames[] frame %zu/%zu pos=%zu",
                        i, frame_count, pos);
                break;
            }
            if (i > 0 && pos + 2 < kWriteReportJsonCap) json[pos++] = ',';

            pos += static_cast<size_t>(snprintf(
                    json + pos,
                    kWriteReportJsonCap - pos,
                    "{\"frame_address\":%" PRIu64 ",\"rel_pc\":%" PRIu64 ",\"load_address\":%" PRIu64
                    ",\"symbol_address\":%" PRIu64 ",\"code_identifier\":",
                    static_cast<uint64_t>(g_write_report_raw_frames[i].frame_address),
                    static_cast<uint64_t>(g_write_report_raw_frames[i].rel_pc),
                    static_cast<uint64_t>(g_write_report_raw_frames[i].load_address),
                    static_cast<uint64_t>(g_write_report_raw_frames[i].symbol_address)));
            append_json_escaped_string(json, kWriteReportJsonCap, &pos,
                    g_write_report_raw_frames[i].code_identifier);
            if (pos + 16 < kWriteReportJsonCap)
                pos += static_cast<size_t>(snprintf(json + pos, kWriteReportJsonCap - pos,
                        ",\"filename\":"));
            append_json_escaped_string(json, kWriteReportJsonCap, &pos,
                    g_write_report_raw_frames[i].filename);
            if (pos + 16 < kWriteReportJsonCap)
                pos += static_cast<size_t>(snprintf(json + pos, kWriteReportJsonCap - pos,
                        ",\"method\":"));
            append_json_escaped_string(json, kWriteReportJsonCap, &pos,
                    g_write_report_raw_frames[i].method);
            if (pos + 4 < kWriteReportJsonCap)
                pos += static_cast<size_t>(snprintf(json + pos, kWriteReportJsonCap - pos, "}"));
        }

        if (pos + 4 < kWriteReportJsonCap) {
            pos += static_cast<size_t>(snprintf(json + pos, kWriteReportJsonCap - pos, "]}"));
        } else {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "write_report: cannot append closing bracket pos=%zu cap=%zu",
                    pos, kWriteReportJsonCap);
        }

        pulse_logd(PULSE_LOG_TAG_CCRASH, "write_report: before safe_write_all");
        safe_write_all(fd, json, strnlen(json, kWriteReportJsonCap));
        close(fd);

        if (g_metadata_source_path[0] != '\0' && g_metadata_file_name[0] != '\0') {
            char metadata_dest[768];
            snprintf(metadata_dest, sizeof(metadata_dest), "%s/%lld_%s/%s",
                    g_reports_dir, ts_ms, session_id, g_metadata_file_name);
            pulse_logd(PULSE_LOG_TAG_CCRASH,
                    "write_report: copying metadata src=%s dest=%s",
                    g_metadata_source_path, metadata_dest);
            copy_file_asyncsafe(g_metadata_source_path, metadata_dest);
        } else if (g_metadata_source_path[0] == '\0') {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "write_report: metadata source path is empty");
        } else {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "write_report: metadata file name is empty");
        }
    }

    size_t signal_index(int sig) __asyncsafe {
        for (size_t i = 0; i < kSignalCount; i++) {
            if (kSignals[i] == sig) return i;
        }
        return kSignalCount;
    }

    void crash_handler(int sig, siginfo_t *info, void *ucontext) __asyncsafe {
        if (g_installed == 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH, "crash_handler: globally was not installed so returning");
            return;
        }

        pulse_logd(PULSE_LOG_TAG_CCRASH, "crash_handler: writing report");

        write_report(sig, info, ucontext);

        const size_t idx = signal_index(sig);
        if (idx < kSignalCount) {
            sigaction(sig, &g_old_actions[idx], nullptr);
        } else {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "crash_handler: signal %d not in kSignals; restoring SIG_DFL", sig);
            signal(sig, SIG_DFL);
        }

        raise(sig);
    }

    /**
     * Register an alternate stack before SA_ONSTACK handlers (bugsnag-plugin-android-ndk
     * bsg_configure_signal_stack). Without this, SA_ONSTACK behavior is undefined vs ignored.
     */
    bool pulse_configure_alt_signal_stack() {
        if (g_alt_stack_sp != nullptr) return true;
        constexpr size_t stack_bytes = SIGSTKSZ * 2;
        g_alt_stack_sp = std::calloc(1, stack_bytes);
        if (g_alt_stack_sp == nullptr) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "pulse_configure_alt_signal_stack: calloc failed size=%zu", stack_bytes);
            return false;
        }
        g_alt_stack.ss_sp = g_alt_stack_sp;
        g_alt_stack.ss_size = stack_bytes;
        g_alt_stack.ss_flags = 0;
        if (sigaltstack(&g_alt_stack, nullptr) != 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "pulse_configure_alt_signal_stack: sigaltstack failed errno=%d", errno);
            std::free(g_alt_stack_sp);
            g_alt_stack_sp = nullptr;
            return false;
        }
        return true;
    }

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_opentelemetry_android_instrumentation_ccrash_PulseNativeJni_nativeInstall(
        JNIEnv *env,
        jobject thiz,
        jstring reportsDirAbsolutePath,
        jstring metadataSourceAbsolutePath,
        jstring crashFileName,
        jstring metadataFileName,
        jstring sessionId) {
    (void) thiz;

    if (g_installed != 0) {
        pulse_logd(PULSE_LOG_TAG_CCRASH, "nativeInstall: already installed, skip");
        return JNI_TRUE;
    }

    const char *path = env->GetStringUTFChars(reportsDirAbsolutePath, nullptr);
    if (path == nullptr) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: reports dir path null");
        return JNI_FALSE;
    }

    strncpy(g_reports_dir, path, sizeof(g_reports_dir) - 1);
    g_reports_dir[sizeof(g_reports_dir) - 1] = '\0';
    env->ReleaseStringUTFChars(reportsDirAbsolutePath, path);

    const char *metadata_path = env->GetStringUTFChars(metadataSourceAbsolutePath, nullptr);
    if (metadata_path == nullptr) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: metadata source path null");
        return JNI_FALSE;
    }

    strncpy(g_metadata_source_path, metadata_path, sizeof(g_metadata_source_path) - 1);
    g_metadata_source_path[sizeof(g_metadata_source_path) - 1] = '\0';
    env->ReleaseStringUTFChars(metadataSourceAbsolutePath, metadata_path);

    const char *crash_file_name = env->GetStringUTFChars(crashFileName, nullptr);
    if (crash_file_name == nullptr) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: crash file name null");
        return JNI_FALSE;
    }

    strncpy(g_crash_file_name, crash_file_name, sizeof(g_crash_file_name) - 1);
    g_crash_file_name[sizeof(g_crash_file_name) - 1] = '\0';
    env->ReleaseStringUTFChars(crashFileName, crash_file_name);

    const char *metadata_file_name = env->GetStringUTFChars(metadataFileName, nullptr);
    if (metadata_file_name == nullptr) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: metadata file name null");
        return JNI_FALSE;
    }

    strncpy(g_metadata_file_name, metadata_file_name, sizeof(g_metadata_file_name) - 1);
    g_metadata_file_name[sizeof(g_metadata_file_name) - 1] = '\0';
    env->ReleaseStringUTFChars(metadataFileName, metadata_file_name);

    const char *session_id = env->GetStringUTFChars(sessionId, nullptr);
    if (session_id == nullptr) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: session id null");
        return JNI_FALSE;
    }

    strncpy(g_session_id, session_id, sizeof(g_session_id) - 1);
    g_session_id[sizeof(g_session_id) - 1] = '\0';
    env->ReleaseStringUTFChars(sessionId, session_id);

    if (g_crash_file_name[0] == '\0' || g_metadata_file_name[0] == '\0') {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: crash or metadata file name empty");
        return JNI_FALSE;
    }

    if (!ensure_dir(g_reports_dir)) return JNI_FALSE;

    if (!pulse_unwind_init()) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: pulse_unwind_init failed");
        return JNI_FALSE;
    }

    if (!pulse_configure_alt_signal_stack()) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeInstall: pulse_configure_alt_signal_stack failed");
        return JNI_FALSE;
    }

    struct sigaction action{};
    memset(&action, 0, sizeof(action));
    action.sa_sigaction = crash_handler;
    sigemptyset(&action.sa_mask);
    action.sa_flags = SA_SIGINFO | SA_ONSTACK;

    for (size_t i = 0; i < kSignalCount; i++) {
        const int sig = kSignals[i];
        if (sigaction(sig, &action, &g_old_actions[i]) != 0) {
            pulse_loge(PULSE_LOG_TAG_CCRASH,
                    "nativeInstall: sigaction failed for signal %d (%s)", sig, signal_name(sig));
            return JNI_FALSE;
        }
        pulse_logd(PULSE_LOG_TAG_CCRASH,
                "nativeInstall: sigaction succeeded for signal %d (%s)", sig, signal_name(sig));
    }

    g_installed = 1;
    pulse_logd(PULSE_LOG_TAG_CCRASH, "nativeInstall: installed");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_opentelemetry_android_instrumentation_ccrash_PulseNativeJni_nativeUpdateSessionId(
        JNIEnv *env,
        jobject thiz,
        jstring sessionId) {
    (void) thiz;

    const char *session_id = env->GetStringUTFChars(sessionId, nullptr);
    if (session_id == nullptr) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "nativeUpdateSessionId: session id null");
        return;
    }

    strncpy(g_session_id, session_id, sizeof(g_session_id) - 1);
    g_session_id[sizeof(g_session_id) - 1] = '\0';
    env->ReleaseStringUTFChars(sessionId, session_id);
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        pulse_loge(PULSE_LOG_TAG_CCRASH, "JNI_OnLoad: GetEnv(JNI_VERSION_1_6) failed");
        return JNI_ERR;
    }
    pulse_logd(PULSE_LOG_TAG_CCRASH, "JNI_OnLoad");
    return JNI_VERSION_1_6;
}
