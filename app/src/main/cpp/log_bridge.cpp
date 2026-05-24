#include "log_bridge.h"
#include <jni.h>
#include <string>
#include <mutex>
#include <android/log.h>

static JavaVM*    g_jvm = nullptr;
static jclass     g_log_cls = nullptr;
static jmethodID  g_log_mid = nullptr;
static std::mutex g_log_mtx;

void log_bridge_init(JavaVM* jvm, jclass cls, jmethodID mid) {
    std::lock_guard<std::mutex> lk(g_log_mtx);
    g_jvm = jvm;
    g_log_mid = mid;
    // Create global ref inside a JNIEnv
    JNIEnv* env = nullptr;
    if (jvm->functions->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        if (g_log_cls) env->DeleteGlobalRef(g_log_cls);
        g_log_cls = (jclass)env->NewGlobalRef(cls);
    }
}

void log_bridge(const char* tag, const char* msg) {
    __android_log_print(ANDROID_LOG_INFO, tag, "%s", msg);
    // Java callback disabled — was causing issues with thread attachment
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libxposed_example_PakActivity_nativeSetLogCallback(
    JNIEnv* env, jclass cls) {

    JavaVM* jvm;
    env->GetJavaVM(&jvm);
    jclass clz = env->FindClass("io/github/libxposed/example/PakActivity");
    jmethodID mid = env->GetStaticMethodID(clz, "appendLog", "(Ljava/lang/String;)V");
    if (clz && mid) log_bridge_init(jvm, clz, mid);
}
