#include <android/log.h>
#include <cstdarg>
#include <cerrno>
#include <fcntl.h>
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

#include "pulse_unwind.h"

namespace {

constexpr const char *kLogTag = "CCrashNative";

constexpr int kSignals[] = {SIGSEGV, SIGABRT, SIGILL, SIGFPE, SIGBUS};
constexpr size_t kSignalCount = sizeof(kSignals) / sizeof(kSignals[0]);

struct sigaction g_old_actions[kSignalCount];

char g_reports_dir[512];
volatile sig_atomic_t g_installed = 0;

pid_t get_tid() {
  return static_cast<pid_t>(syscall(SYS_gettid));
}

const char *signal_name(int sig) {
  switch (sig) {
    case SIGSEGV:
      return "SIGSEGV";
    case SIGABRT:
      return "SIGABRT";
    case SIGILL:
      return "SIGILL";
    case SIGFPE:
      return "SIGFPE";
    case SIGBUS:
      return "SIGBUS";
    default:
      return "SIG?";
  }
}

void logd(const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  __android_log_vprint(ANDROID_LOG_DEBUG, kLogTag, fmt, ap);
  va_end(ap);
}

void loge(const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  __android_log_vprint(ANDROID_LOG_ERROR, kLogTag, fmt, ap);
  va_end(ap);
}

void safe_write_all(int fd, const char *buf, size_t len) {
  size_t off = 0;
  while (off < len) {
    ssize_t rc = write(fd, buf + off, len - off);
    if (rc <= 0) {
      break;
    }
    off += static_cast<size_t>(rc);
  }
}

void write_report(int sig, siginfo_t *info) {
  if (g_reports_dir[0] == '\0') {
    loge("pulse c-crash write_report: global reports dir is empty");
    return;
  }

  timespec ts {};
  clock_gettime(CLOCK_REALTIME, &ts);
  const long long ts_ms = static_cast<long long>(ts.tv_sec) * 1000LL + ts.tv_nsec / 1000000LL;

  char path[768];
  const int pid = static_cast<int>(getpid());
  const int tid = static_cast<int>(get_tid());
  snprintf(path, sizeof(path), "%s/pulse-native-crash-%lld-%d-%d.json", g_reports_dir, ts_ms, pid, tid);

  logd("pulse c-crash write_report: opening path = %s", path);
  int fd = open(path, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
  if (fd == -1) {
    loge("pulse c-crash write_report: failed to open path = %s", path);
    return;
  }

  pulse::UnwindFrame frames[64];
  const size_t frame_count = pulse::unwind_current_thread(frames, 64, 2);

  char json[8192];
  size_t pos = 0;

  const uintptr_t fault_addr = info ? reinterpret_cast<uintptr_t>(info->si_addr) : 0;

  pos += static_cast<size_t>(snprintf(
      json + pos,
      sizeof(json) - pos,
      "{"
      "\"ts_ms\":%lld,"
      "\"pid\":%d,"
      "\"tid\":%d,"
      "\"signal\":%d,"
      "\"signal_name\":\"%s\","
      "\"fault_addr\":\"0x%lx\","
      "\"stack\":[",
      ts_ms,
      pid,
      tid,
      sig,
      signal_name(sig),
      static_cast<unsigned long>(fault_addr)));

  for (size_t i = 0; i < frame_count && pos < sizeof(json); i++) {
    pos += static_cast<size_t>(
        snprintf(json + pos, sizeof(json) - pos, "\"0x%lx\"%s", static_cast<unsigned long>(frames[i].pc),
                 (i + 1 < frame_count) ? "," : ""));
  }

  if (pos < sizeof(json)) {
    pos += static_cast<size_t>(snprintf(json + pos, sizeof(json) - pos, "]}"));
  }

  safe_write_all(fd, json, strnlen(json, sizeof(json)));
  close(fd);
}

size_t signal_index(int sig) {
  for (size_t i = 0; i < kSignalCount; i++) {
    if (kSignals[i] == sig) {
      return i;
    }
  }
  return kSignalCount;
}

void crash_handler(int sig, siginfo_t *info, void *ucontext) {
  (void)ucontext;

  if (g_installed == 0) {
    loge("pulse c-crash crash_handler: globally was not installed so returning");
    return;
  }

  logd("pulse c-crash crash_handler: writing report");

  write_report(sig, info);

  const size_t idx = signal_index(sig);
  if (idx < kSignalCount) {
    sigaction(sig, &g_old_actions[idx], nullptr);
  } else {
    signal(sig, SIG_DFL);
  }

  raise(sig);
}

bool ensure_dir(const char *path) {
  if (path == nullptr || path[0] == '\0') {
    return false;
  }
  if (mkdir(path, 0700) == 0) {
    return true;
  }
  if (errno == EEXIST) {
    return true;
  }
  return false;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_opentelemetry_android_instrumentation_ccrash_PulseNativeJni_nativeInstall(
    JNIEnv *env, jobject thiz, jstring reportsDirAbsolutePath) {
  (void)thiz;

  const char *path = env->GetStringUTFChars(reportsDirAbsolutePath, nullptr);
  if (path == nullptr) {
    loge("pulse c-crash nativeInstall: path null");
    return JNI_FALSE;
  }

  strncpy(g_reports_dir, path, sizeof(g_reports_dir) - 1);
  g_reports_dir[sizeof(g_reports_dir) - 1] = '\0';
  env->ReleaseStringUTFChars(reportsDirAbsolutePath, path);

  if (!ensure_dir(g_reports_dir)) {
    loge("pulse c-crash nativeInstall: ensure_dir failed");
    return JNI_FALSE;
  }

  struct sigaction action {};
  memset(&action, 0, sizeof(action));
  action.sa_sigaction = crash_handler;
  sigemptyset(&action.sa_mask);
  action.sa_flags = SA_SIGINFO | SA_ONSTACK;

  for (size_t i = 0; i < kSignalCount; i++) {
    const int sig = kSignals[i];
    if (sigaction(sig, &action, &g_old_actions[i]) != 0) {
      loge("pulse c-crash nativeInstall: sigaction failed for signal %d (%s)", sig, signal_name(sig));
      return JNI_FALSE;
    }
    logd("pulse c-crash nativeInstall: sigaction succeeded for signal %d (%s)", sig, signal_name(sig));
  }

  g_installed = 1;
  logd("pulse c-crash nativeInstall: installed");
  return JNI_TRUE;
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)reserved;
  JNIEnv *env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }
  logd("pulse c-crash JNI_OnLoad");
  return JNI_VERSION_1_6;
}

