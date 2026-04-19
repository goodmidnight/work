#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include "PeerNode.hpp"

// Define the target Kotlin/Java class path
#define JNI_CLASS_PATH "io/goodmidnight/transfer/TransferEngine"

using namespace transfer::core;

// Engine instances
static std::unique_ptr<PeerNode> global_engine = nullptr;
static std::mutex engine_mutex;

// Global references for JNI IDs to avoid expensive lookups during runtime.
static JavaVM* g_jvm = nullptr;
static jclass g_transferEngineClass = nullptr;
static jmethodID g_onTransferEventMethod = nullptr;

// Internal utility to initialize the engine safely
void init_engine_if_needed() {
    if (!global_engine) {
        global_engine = std::make_unique<PeerNode>();

        global_engine->set_transfer_callback([](const std::string& fileName, TransferState state, int progress, const std::string& msg) {
            if (!g_jvm || !g_transferEngineClass || !g_onTransferEventMethod) return;

            JNIEnv* env;
            bool attached = false;

            int getEnvStat = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (getEnvStat == JNI_EDETACHED) {
                if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) return;
                attached = true;
            } else if (getEnvStat == JNI_EVERSION) {
                return;
            }

            jstring jFileName = env->NewStringUTF(fileName.c_str());
            jstring jMsg = env->NewStringUTF(msg.c_str());

            // Use the globally cached Class and Method ID (Lightning fast)
            env->CallStaticVoidMethod(
                g_transferEngineClass,
                g_onTransferEventMethod,
                jFileName,
                static_cast<jint>(state),
                static_cast<jint>(progress),
                jMsg
            );

            env->DeleteLocalRef(jFileName);
            env->DeleteLocalRef(jMsg);

            if (attached) {
                g_jvm->DetachCurrentThread();
            }
        });
    }
}

// ========================================================================
// Native Implementation Functions (Clean names, no JNIEXPORT boilerplate)
// ========================================================================

static jboolean nativeStartListening(JNIEnv *env, jobject thiz, jint port) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    init_engine_if_needed();
    global_engine->start();
    return global_engine->start_listening(port);
}

static void nativeConnectToPeer(JNIEnv *env, jobject thiz, jstring ip, jint port, jint session_count) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    init_engine_if_needed();

    const char *native_ip = env->GetStringUTFChars(ip, 0);
    if (native_ip != nullptr) {
        global_engine->start();
        global_engine->connect_to_peer(native_ip, port, session_count);
        env->ReleaseStringUTFChars(ip, native_ip);
    }
}

static void nativeSendFile(JNIEnv *env, jobject thiz, jstring file_path) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (global_engine) {
        const char *native_path = env->GetStringUTFChars(file_path, 0);
        if (native_path != nullptr) {
            global_engine->send_file(native_path);
            env->ReleaseStringUTFChars(file_path, native_path);
        }
    }
}

static void nativeStopEngine(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (global_engine) {
        global_engine->stop();
        global_engine.reset();
    }
}

// ========================================================================
// Method Mapping Array for RegisterNatives
// Signature format: (ArgumentTypes)ReturnType
// ========================================================================
static JNINativeMethod g_methods[] = {
    {"startListening", "(I)Z", (void*)nativeStartListening},
    {"connectToPeer",  "(Ljava/lang/String;II)V", (void*)nativeConnectToPeer},
    {"sendFile",       "(Ljava/lang/String;)V", (void*)nativeSendFile},
    {"stopEngine",     "()V", (void*)nativeStopEngine}
};

// ========================================================================
// JNI Lifecycle (Caching & Registration)
// ========================================================================

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    JNIEnv* env;

    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Find the class using the Macro
    jclass localClass = env->FindClass(JNI_CLASS_PATH);
    if (!localClass) return JNI_ERR;

    // Register native methods dynamically
    int numMethods = sizeof(g_methods) / sizeof(g_methods[0]);
    if (env->RegisterNatives(localClass, g_methods, numMethods) < 0) {
        env->DeleteLocalRef(localClass);
        return JNI_ERR;
    }

    // Cache the Class and Method ID globally
    g_transferEngineClass = (jclass)env->NewGlobalRef(localClass);
    g_onTransferEventMethod = env->GetStaticMethodID(
        g_transferEngineClass,
        "onTransferEvent",
        "(Ljava/lang/String;IILjava/lang/String;)V"
    );

    env->DeleteLocalRef(localClass);

    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        // Clear cached references to prevent memory leaks
        if (g_transferEngineClass) {
            // Optional: Unregister natives, though Android handles this automatically on unload
            env->UnregisterNatives(g_transferEngineClass);
            env->DeleteGlobalRef(g_transferEngineClass);
            g_transferEngineClass = nullptr;
        }
        g_onTransferEventMethod = nullptr;
    }
}