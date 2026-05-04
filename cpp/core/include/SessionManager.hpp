#pragma once

#include <vector>
#include <memory>
#include <mutex>
#include <string>
#include <functional>

namespace transfer::core {

    // Forward declaration to avoid circular dependencies with Session.hpp[cite: 16]
    class Session;

    /**
     * @enum TransferState
     * @brief Constants representing the current state of a file transfer.
     *        These values strictly match the Enums defined in the Kotlin (JNI) layer.[cite: 16]
     */
    enum class TransferState {
        STARTED = 0,
        PROGRESS = 1,
        COMPLETED = 2,
        ERROR = -1,
        CONNECTED = 3
    };

    // Callback used to send transfer events (progress, completion, errors) up to the UI/JNI layer.[cite: 16]
    using TransferCallback = std::function<void(const std::string&, TransferState, int, const std::string&)>;

    // Callback used to request platform-specific file descriptors (e.g., from Android SAF).[cite: 16]
    using FdRequestCallback = std::function<int(const std::string&)>;

    /**
     * @class SessionManager
     * @brief The core orchestrator for parallel file transfers.
     *        It manages multiple TCP sessions (pipes), splits outgoing files into equal chunks,
     *        and aggregates the status (success/failure) of all active pipes.[cite: 16]
     */
    class SessionManager {
    public:
        SessionManager() = default;

        /**
         * @brief Registers a newly established TCP session to the active pool.
         * @param session A shared pointer to the connected Session object.
         */
        void add_session(std::shared_ptr<Session> session);

        /**
         * @brief Initiates the parallel transfer of a file by dividing it among all active sessions.
         * @param file_path The absolute path of the file to transmit.
         */
        void push_to_single_peer(const std::string& file_path);

        /**
         * @brief Aggregation callback invoked by individual pipes when they finish their assigned chunk.
         * @param is_success True if the pipe successfully sent/received its entire chunk.
         */
        void notify_transfer_finished(bool is_success);

        /**
         * @brief Safely propagates an error to the UI layer and marks the global transfer state as compromised.
         * @param file_name The name of the file that encountered the error.
         * @param error_msg A descriptive error message.
         */
        void notify_transfer_error(const std::string& file_name, const std::string& error_msg);

        // --- Dependency Injections ---
        void set_callback(TransferCallback cb) { callback_ = std::move(cb); }
        TransferCallback get_callback() const { return callback_; }

        void set_fd_callback(FdRequestCallback cb) { fd_callback_ = std::move(cb); }
        FdRequestCallback get_fd_callback() const { return fd_callback_; }

        /**
         * @brief Sets the 32-byte AES-GCM key for E2E encryption.
         *        If left empty, the engine falls back to ultra-fast Zero-Copy plaintext transmission.[cite: 16]
         */
        void set_encryption_key(const std::string& key) { encryption_key_ = key; }
        const std::string& get_encryption_key() const { return encryption_key_; }

    private:
        // Pool of active TCP connections (pipes)[cite: 16]
        std::vector<std::shared_ptr<Session>> sessions_;

        // Mutex to protect shared states (sessions_, active_transfers_count_, etc.) from concurrent ASIO thread access[cite: 16]
        std::mutex mutex_;

        TransferCallback callback_;
        FdRequestCallback fd_callback_;
        std::string encryption_key_;

        // State tracking variables for the currently active file broadcast[cite: 16]
        std::string active_file_path_;
        bool is_broadcasting_ = false;
        bool has_error_ = false;

        // Countdown latch: Tracks how many pipes are still actively transferring data[cite: 16]
        int active_transfers_count_ = 0;
    };

} // namespace transfer::core