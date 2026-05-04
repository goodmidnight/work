package io.goodmidnight.transfer.data.jni

import android.util.Log
import io.goodmidnight.transfer.data.exception.DataException

/**
 * @class TransferEngine
 * @brief A singleton wrapper object that acts as the sole bridge between the JVM (Kotlin)
 *        and the Native C++ (JNI) layer.
 *
 * Following Clean Architecture principles, this object should only be invoked by the
 * Data Layer (Repository). The UI layer must never interact with this engine directly.
 */
object TransferEngine {

    // The name of the compiled C++ shared library (.so)
    // This must exactly match the target_link_libraries name in CMakeLists.txt
    private const val LIB_NAME = "transfer_core"

    init {
        try {
            System.loadLibrary(LIB_NAME)
            Log.i("TransferEngine", "Native library '$LIB_NAME' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("TransferEngine", "Failed to load native library: ${e.message}")
            throw DataException.NativeLibraryException(
                message = "C++ Core Library ($LIB_NAME) failed to load. The app cannot perform file transfers.",
                cause = e
            )
        }
    }

    // ========================================================================
    // Kotlin -> C++ (Native Method Declarations)
    // ========================================================================

    /**
     * [Receiver / Hub Mode]
     * Opens a socket on the specified port and listens for incoming connections.
     *
     * @param port The TCP port to bind to.
     * @return True if listening started successfully, false otherwise.
     */
    external fun startReceiver(port: Int): Boolean

    /**
     * [Sender / Spoke Mode]
     * Establishes multiple parallel TCP connections to the target IP address.
     *
     * @param ip The target device's IPv4 address.
     * @param port The target TCP port.
     * @param sessionCount The number of parallel pipes to establish (default is 4 for max throughput).
     */
    external fun startSender(ip: String, port: Int, sessionCount: Int = 4)

    /**
     * Enqueues a file to be pushed across the active network pipes.
     *
     * @param filePath The absolute path or identifier of the file to send.
     */
    external fun pushFile(filePath: String)

    /**
     * Gracefully stops the native engine, closing all sockets and terminating ASIO threads.
     */
    external fun stopEngine()


    // ========================================================================
    // C++ -> Kotlin (Callback Bridge)
    // ========================================================================

    // Listeners registered by the Repository layer
    private var transferCallback: ((fileName: String, stateCode: Int, progress: Int, msg: String) -> Unit)? = null
    private var fdRequestCallback: ((fileName: String) -> Int)? = null

    /**
     * Registers a callback to receive real-time transfer events from the C++ layer.
     */
    fun setCallback(listener: ((fileName: String, stateCode: Int, progress: Int, msg: String) -> Unit)?) {
        transferCallback = listener
    }

    /**
     * Registers a callback to handle File Descriptor (FD) requests.
     * This is crucial for Android 10+ (Scoped Storage), where C++ cannot access files by raw path.
     */
    fun setFdRequestCallback(listener: ((fileName: String) -> Int)?) {
        fdRequestCallback = listener
    }

    /**
     * @brief Triggered by the Native C++ ASIO Worker Thread.
     *
     * @JvmStatic is mandatory here. It instructs the Kotlin compiler to generate a true
     * static method at the bytecode level, allowing C++ `GetStaticMethodID` to find it
     * without needing an object instance.
     *
     * @param fileName The name of the file being processed.
     * @param stateCode Mapping: 0=STARTED, 1=PROGRESS, 2=COMPLETED, 3=CONNECTED, -1=ERROR.
     * @param progress The transfer progress percentage (0 to 100).
     * @param msg A descriptive message or error trace.
     */
    @JvmStatic
    fun onTransferEvent(fileName: String, stateCode: Int, progress: Int, msg: String) {
        // WARNING: This is called from a background thread!
        // Do NOT update UI directly from this callback. The Repository must use
        // Coroutines (e.g., Dispatchers.Main) or LiveData/StateFlow to push updates to the UI.
        transferCallback?.invoke(fileName, stateCode, progress, msg)
    }

    /**
     * @brief Triggered by the Native C++ layer when it needs physical access to a file.
     *
     * C++ passes the fileName, and Kotlin uses Android's ContentResolver / SAF to open
     * the file and returns the raw UNIX File Descriptor (Int).
     *
     * @param fileName The name of the file C++ wants to read or write.
     * @return The raw Linux File Descriptor (FD), or -1 if acquisition failed.
     */
    @JvmStatic
    fun requestFileDescriptor(fileName: String): Int {
        return fdRequestCallback?.invoke(fileName) ?: -1
    }
}