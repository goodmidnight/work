#pragma once

#include <string>
#include <functional>

namespace transfer::core {

    /**
     * @enum TransferState
     * @brief Constants representing the current state of a file transfer.
     */
    enum class TransferState {
        STARTED = 0,
        PROGRESS = 1,
        COMPLETED = 2,
        ERROR = -1,
        CONNECTED = 3
    };

    // Callback used to send transfer events (progress, completion, errors) up to the UI/JNI layer.
    using TransferCallback = std::function<void(const std::string& file_name, TransferState state, int progress, const std::string& message)>;

    // Callback used to request platform-specific file descriptors (e.g., from Android SAF).
    using FdRequestCallback = std::function<int(const std::string& file_path)>;

} // namespace transfer::core