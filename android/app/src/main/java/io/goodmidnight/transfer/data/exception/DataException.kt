package io.goodmidnight.transfer.data.exception

import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.ErrorCode

/**
 * A sealed class for all exceptions originating from the Data layer.
 */
sealed class DataException(
    override val message: String,
    override val cause: Throwable? = null,
    override val errorCode: ErrorCode = DataErrorCode.UNKNOWN_DATA_ERROR,
) : ApplicationException(message, cause, errorCode) {

    /**
     * Thrown when DataStore fails to read preferences (e.g., corrupted file).
     */
    data class DataStoreReadException(
        override val message: String = "Failed to read DataStore preferences.",
        override val cause: Throwable? = null
    ) : DataException(message, cause, DataErrorCode.DATASTORE_READ_ERROR)

    /**
     * Thrown when DataStore fails to save preferences.
     */
    data class DataStoreWriteException(
        override val message: String = "Failed to save DataStore preferences.",
        override val cause: Throwable? = null
    ) : DataException(message, cause, DataErrorCode.DATASTORE_WRITE_ERROR)

    /**
     * Thrown when the C++ native library (.so) fails to load into memory.
     */
    data class NativeLibraryException(
        override val message: String,
        override val cause: Throwable? = null
    ) : DataException(message, cause, DataErrorCode.NATIVE_LIBRARY_LOAD_ERROR)

    /**
     * Thrown when the C++ transfer engine reports a failure during file transmission.
     */
    data class TransferEngineException(
        override val message: String,
        override val cause: Throwable? = null
    ) : DataException(message, cause, DataErrorCode.TRANSFER_ENGINE_ERROR)
}