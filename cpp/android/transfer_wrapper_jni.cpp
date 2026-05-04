#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include "TransferEngine.hpp"

// The exact package path of the Kotlin object/class that contains the external native functions.[cite: 20]
#define JNI_CLASS_PATH "io/goodmidnight/transfer/data/jni/TransferEngine"

using namespace transfer::core;

/**
 * @struct JniContext
 * @brief Encapsulates all global JNI references.
 *        Wrapping these in a struct prevents Clangd IDE parsing errors (e.g., "does not refer to a value")
 *        and keeps the global namespace clean.[cite: 20]
 */
struct JniContext {
    JavaVM* jvm = nullptr;
    jclass engineClass = nullptr;
    jmethodID onEventMethod = nullptr;
    jmethodID requestFdMethod = nullptr;
};

static JniContext g_jni;

// Singleton instance of the core engine and a mutex to ensure thread-safe initialization and destruction.[cite: 20]
static std::unique_ptr<TransferEngine> global_engine = nullptr;
static std::mutex engine_mutex;

void init_engine_if_needed() {
    if (!global_engine) {
        global_engine = std::make_unique<TransferEngine>();

        // --- Transfer Event Callback (Engine -> UI) ---
        global_engine->set_transfer_callback(
            [](const std::string &fileName, TransferState state, int progress, const std::string &msg) {
                if (!g_jni.jvm || !g_jni.engineClass || !g_jni.onEventMethod) return;

                JNIEnv *env;
                bool attached = false;

                // CRITICAL: This callback is fired from a background C++ ASIO worker thread.
                // Pure C++ threads cannot interact with Java directly. We MUST "Attach" the thread to the JVM first.[cite: 20]
                if (g_jni.jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_EDETACHED) {
                    if (g_jni.jvm->AttachCurrentThread(&env, nullptr) != 0) return;
                    attached = true;
                }

                // Convert C++ std::string to Java jstring
                jstring jFileName = env->NewStringUTF(fileName.c_str());
                jstring jMsg = env->NewStringUTF(msg.c_str());

                // Invoke the static Kotlin method: onTransferEvent(String, Int, Int, String)
                env->CallStaticVoidMethod(g_jni.engineClass, g_jni.onEventMethod,
                                          jFileName, static_cast<jint>(state), static_cast<jint>(progress), jMsg);

                // Prevent memory leaks by explicitly deleting local JNI references
                env->DeleteLocalRef(jFileName);
                env->DeleteLocalRef(jMsg);

                // Detach the thread if we temporarily attached it above to avoid crashing the JVM[cite: 20]
                if (attached) g_jni.jvm->DetachCurrentThread();
            });

        // --- File Descriptor Request Callback (Engine -> Platform OS) ---
        global_engine->set_fd_request_callback([](const std::string &fileName) -> int {
            if (!g_jni.jvm || !g_jni.engineClass || !g_jni.requestFdMethod) return -1;

            JNIEnv *env;
            bool attached = false;

            // Attach C++ thread to JVM to call Android's Storage Access Framework (SAF) logic
            if (g_jni.jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_EDETACHED) {
                if (g_jni.jvm->AttachCurrentThread(&env, nullptr) != 0) return -1;
                attached = true;
            }

            jstring jFileName = env->NewStringUTF(fileName.c_str());

            // Invoke the static Kotlin method: requestFileDescriptor(String): Int
            jint fd = env->CallStaticIntMethod(g_jni.engineClass, g_jni.requestFdMethod, jFileName);

            env->DeleteLocalRef(jFileName);
            if (attached) g_jni.jvm->DetachCurrentThread();

            return static_cast<int>(fd);
        });
    }
}

static jboolean nativeStartReceiver(JNIEnv *env, jobject thiz, jint port) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    init_engine_if_needed();
    return global_engine->startReceiver(port);
}

static void nativeStartSender(JNIEnv *env, jobject thiz, jstring ip, jint port, jint session_count) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    init_engine_if_needed();

    // Convert Java jstring to C-style string. This allocates memory in the JNI layer.[cite: 20]
    const char *native_ip = env->GetStringUTFChars(ip, 0);
    if (native_ip) {
        global_engine->startSender(native_ip, port, session_count);

        // CRITICAL: Must release the string characters to prevent memory leaks.[cite: 20]
        env->ReleaseStringUTFChars(ip, native_ip);
    }
}

static void nativePushFile(JNIEnv *env, jobject thiz, jstring file_path) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (global_engine) {
        const char *native_path = env->GetStringUTFChars(file_path, 0);
        if (native_path) {
            global_engine->pushFile(native_path);
            env->ReleaseStringUTFChars(file_path, native_path);
        }
    }
}

static void nativeStopEngine(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (global_engine) {
        global_engine->stop();
        global_engine.reset(); // Safely destroys the engine and underlying ASIO context
    }
}

// Map Java method names to their corresponding C++ function pointers
static JNINativeMethod g_methods[] = {
    {"startReceiver", "(I)Z", (void *) nativeStartReceiver},
    {"startSender", "(Ljava/lang/String;II)V", (void *) nativeStartSender},
    {"pushFile", "(Ljava/lang/String;)V", (void *) nativePushFile},
    {"stopEngine", "()V", (void *) nativeStopEngine}
};

/**
 * @brief Called exactly once when the Android OS loads this shared library (.so).
 *        Used to globally cache class and method IDs for maximum performance.
 */
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jni.jvm = vm; // Cache the Java Virtual Machine pointer[cite: 20]
    JNIEnv *env;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Find the Kotlin target class. This returns a "Local Reference".
    jclass localClass = env->FindClass(JNI_CLASS_PATH);
    if (!localClass) return JNI_ERR;

    // Register our native functions mapped in g_methods
    int numMethods = sizeof(g_methods) / sizeof(g_methods[0]);
    if (env->RegisterNatives(localClass, g_methods, numMethods) < 0) {
        env->DeleteLocalRef(localClass);
        return JNI_ERR;
    }

    // Upgrade the local reference to a Global Reference so it survives past JNI_OnLoad.
    // Without this, the app will crash when attempting to use the class later.[cite: 20]
    g_jni.engineClass = (jclass) env->NewGlobalRef(localClass);

    // Method IDs are safe to cache globally without NewGlobalRef.
    g_jni.onEventMethod = env->GetStaticMethodID(
        g_jni.engineClass,
        "onTransferEvent",
        "(Ljava/lang/String;IILjava/lang/String;)V"
    );
    g_jni.requestFdMethod = env->GetStaticMethodID(
        g_jni.engineClass,
        "requestFileDescriptor",
        "(Ljava/lang/String;)I"
    );

    // Clean up the temporary local reference
    env->DeleteLocalRef(localClass);

    return JNI_VERSION_1_6;
}

/**
 * @brief Called when the library is being unloaded. Clean up global references to prevent leaks.
 */
extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_jni.engineClass) {
            env->UnregisterNatives(g_jni.engineClass);
            env->DeleteGlobalRef(g_jni.engineClass); // Explicitly release the Global Reference[cite: 20]
            g_jni.engineClass = nullptr;
        }
        g_jni.onEventMethod = nullptr;
        g_jni.requestFdMethod = nullptr;
        g_jni.jvm = nullptr;
    }
}