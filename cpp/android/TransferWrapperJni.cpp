#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include "TransferEngine.hpp"
#include "Logger.hpp"

#define JNI_CLASS_PATH "io/goodmidnight/transfer/data/jni/TransferEngine"

using namespace transfer::core;

struct JniContext {
    JavaVM* jvm = nullptr;
    jclass engineClass = nullptr;
    jmethodID onEventMethod = nullptr;
    jmethodID requestFdMethod = nullptr;
};

static JniContext g_jni;
static std::unique_ptr<TransferEngine> global_engine = nullptr;
static std::mutex engine_mutex;

void init_engine_if_needed() {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (!global_engine) {
        global_engine = std::make_unique<TransferEngine>();

        global_engine->set_transfer_callback(
            [](const std::string &fileName, TransferState state, int progress, const std::string &msg) {
                if (!g_jni.jvm || !g_jni.engineClass || !g_jni.onEventMethod) return;

                JNIEnv *env;
                bool attached = false;
                if (g_jni.jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_EDETACHED) {
                    if (g_jni.jvm->AttachCurrentThread(&env, nullptr) != 0) return;
                    attached = true;
                }

                jstring jFileName = env->NewStringUTF(fileName.c_str());
                jstring jMsg = env->NewStringUTF(msg.c_str());
                env->CallStaticVoidMethod(g_jni.engineClass, g_jni.onEventMethod,
                                          jFileName, static_cast<jint>(state), static_cast<jint>(progress), jMsg);
                env->DeleteLocalRef(jFileName);
                env->DeleteLocalRef(jMsg);

                if (attached) g_jni.jvm->DetachCurrentThread();
            });

        global_engine->set_fd_request_callback([](const std::string &fileName) -> int {
            if (!g_jni.jvm || !g_jni.engineClass || !g_jni.requestFdMethod) return -1;

            JNIEnv *env;
            bool attached = false;
            if (g_jni.jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_EDETACHED) {
                if (g_jni.jvm->AttachCurrentThread(&env, nullptr) != 0) return -1;
                attached = true;
            }

            jstring jFileName = env->NewStringUTF(fileName.c_str());
            jint fd = env->CallStaticIntMethod(g_jni.engineClass, g_jni.requestFdMethod, jFileName);
            env->DeleteLocalRef(jFileName);

            if (attached) g_jni.jvm->DetachCurrentThread();
            return static_cast<int>(fd);
        });
        LOGI("TransferEngine initialized via JNI.");
    }
}

/**
 * Standard JNI naming allows the IDE to resolve functions and enables navigation.
 * Since Kotlin side uses @JvmStatic on 'object TransferEngine' methods,
 * the second parameter must be 'jclass' (not jobject).
 */
extern "C" {

JNIEXPORT jboolean JNICALL
Java_io_goodmidnight_transfer_data_jni_TransferEngine_startReceiver(JNIEnv *env, jclass clazz, jint port, jstring save_path) {
    init_engine_if_needed();
    const char *native_save_path = env->GetStringUTFChars(save_path, nullptr);
    if (!native_save_path) {
        LOGE("JNI: save_path is null in startReceiver.");
        return JNI_FALSE;
    }
    bool result = global_engine->startReceiver(static_cast<uint16_t>(port), native_save_path);
    env->ReleaseStringUTFChars(save_path, native_save_path);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_goodmidnight_transfer_data_jni_TransferEngine_startSender(JNIEnv *env, jclass clazz, jstring ip, jint port, jstring file_path) {
    init_engine_if_needed();
    const char *native_ip = env->GetStringUTFChars(ip, nullptr);
    const char *native_file_path = env->GetStringUTFChars(file_path, nullptr);

    if (native_ip && native_file_path) {
        global_engine->startSender(native_ip, static_cast<uint16_t>(port), native_file_path);
    } else {
        LOGE("JNI: IP or file_path is null in startSender.");
    }

    if (native_ip) env->ReleaseStringUTFChars(ip, native_ip);
    if (native_file_path) env->ReleaseStringUTFChars(file_path, native_file_path);
}

JNIEXPORT void JNICALL
Java_io_goodmidnight_transfer_data_jni_TransferEngine_stopEngine(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (global_engine) {
        global_engine->stop();
        global_engine.reset();
        LOGI("TransferEngine stopped and destroyed via JNI.");
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jni.jvm = vm;
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass localClass = env->FindClass(JNI_CLASS_PATH);
    if (!localClass) return JNI_ERR;

    // We no longer need RegisterNatives because we use standard JNI naming.

    g_jni.engineClass = (jclass) env->NewGlobalRef(localClass);
    env->DeleteLocalRef(localClass);

    g_jni.onEventMethod = env->GetStaticMethodID(g_jni.engineClass, "onTransferEvent", "(Ljava/lang/String;IILjava/lang/String;)V");
    g_jni.requestFdMethod = env->GetStaticMethodID(g_jni.engineClass, "requestFileDescriptor", "(Ljava/lang/String;)I");

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_jni.engineClass) {
            env->DeleteGlobalRef(g_jni.engineClass);
            g_jni.engineClass = nullptr;
        }
    }
}

} // extern "C"
