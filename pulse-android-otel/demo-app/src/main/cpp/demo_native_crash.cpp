#include <jni.h>
#include <csignal>
#include <cstdlib>

namespace {

void __attribute__((used)) some_fake_func() {}

int crash_write_read_only() {
  volatile char *ptr = (char *)some_fake_func;
  *ptr = 0;
  return 5;
}

void native_crash_chain_level2() {
  (void)crash_write_read_only();
}

void native_crash_chain_level1() {
  native_crash_chain_level2();
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_io_opentelemetry_android_demo_DemoNativeCrash_nativeAbort(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;
  abort();
}

extern "C" JNIEXPORT void JNICALL
Java_io_opentelemetry_android_demo_DemoNativeCrash_nativeSigsegv(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;
  raise(SIGSEGV);
}

extern "C" JNIEXPORT void JNICALL
Java_io_opentelemetry_android_demo_DemoNativeCrash_nativeReadOnlyCrash(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;
  native_crash_chain_level1();
}
