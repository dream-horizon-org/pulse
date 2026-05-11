#include <jni.h>
#include <signal.h>
#include <stdlib.h>

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
