package io.goodmidnight.transfer.data.exception

import io.goodmidnight.transfer.core.exception.ErrorCode

/**
 * Enumeration of error codes specific to the Data layer.
 * Includes errors related to DataStore, JNI (C++ Engine), and socket networking.
 */
enum class DataErrorCode(
    override val message: String? = null,
    override val code: String,
) : ErrorCode {
    
    // --- DataStore Errors ---
    DATASTORE_READ_ERROR(message = "Failed to read from DataStore", code = "DATA_001"),
    DATASTORE_WRITE_ERROR(message = "Failed to write to DataStore", code = "DATA_002"),
    
    // --- Native Engine (C++) Errors ---
    NATIVE_LIBRARY_LOAD_ERROR(message = "Failed to load the native C++ library", code = "DATA_010"),
    TRANSFER_ENGINE_ERROR(message = "An error occurred inside the C++ transfer engine", code = "DATA_011"),
    
    // --- Networking Errors ---
    NETWORK_CONNECTION_ERROR(message = "Failed to establish a P2P socket connection", code = "DATA_020"),
    
    // --- Fallback ---
    UNKNOWN_DATA_ERROR(message = "Unknown Data Layer Error", code = "DATA_999")
}