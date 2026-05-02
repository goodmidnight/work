package io.goodmidnight.transfer.data.jni

import android.util.Log

/**
 * A singleton engine wrapper class that communicates directly with the underlying C++ JNI layer.
 * Following Clean Architecture principles, only the Data Layer (Repository) should invoke this object.
 */
object TransferEngine {

    // The name of the shared library built from C++ (Must match the target name in CMakeLists.txt)
    // e.g., if target_link_libraries(transfer_core ...), then this should be "transfer_core"
    private const val LIB_NAME = "transfer_core"

    init {
        try {
            System.loadLibrary(LIB_NAME)
            Log.i("TransferEngine", "Native library '$LIB_NAME' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("TransferEngine", "Failed to load native library: ${e.message}")
        }
    }

    // ========================================================================
    // Kotlin -> C++ (Native Functions)
    // ========================================================================

    /**
     * [Hub/Receiver Mode] Starts listening on the specified port for incoming socket connections.
     */
    external fun startListening(port: Int): Boolean

    /**
     * [Spoke/Sender Mode] Opens a P2P socket pipe (session) to the target IP and port.
     *
     * @param sessionCount Default is 4 (for high-speed multi-pipe transfers).
     */
    external fun connectToPeer(ip: String, port: Int, sessionCount: Int = 4)

    /**
     * Enqueues the file at the specified absolute path into the C++ engine and starts the transfer.
     */
    external fun sendFile(filePath: String)

    /**
     * Stops the engine and releases all underlying socket resources.
     */
    external fun stopEngine()

    // ========================================================================
    // C++ -> Kotlin (Callback Mechanism)
    // ========================================================================

    // A variable to hold the lambda callback from the Kotlin layer (Repository)
    private var transferCallback: ((fileName: String, stateCode: Int, progress: Int, msg: String) -> Unit)? = null

    /**
     * Used externally (by the Repository) to register a callback listener.
     */
    fun setCallback(listener: ((fileName: String, stateCode: Int, progress: Int, msg: String) -> Unit)?) {
        transferCallback = listener
    }

    /**
     * A static method called directly from the C++ JNI environment (using CallStaticVoidMethod).
     * The signature must exactly match what is expected by JNI's GetStaticMethodID.
     *
     * Since C++ cannot natively understand or invoke Kotlin lambdas directly,
     * this static method acts as a global mapping bridge.
     *
     * @param fileName The name of the file currently being processed.
     * @param stateCode State code (0: STARTED, 1: PROGRESS, 2: COMPLETED, -1: ERROR).
     * @param progress Transfer progress (0 to 100).
     * @param msg Error or status message.
     */
    @JvmStatic
    fun onTransferEvent(fileName: String, stateCode: Int, progress: Int, msg: String) {
        // Safely pass the event back to the Kotlin callback.
        // Note: This is invoked from a background C++ thread.
        transferCallback?.invoke(fileName, stateCode, progress, msg)
    }
}