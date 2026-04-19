#pragma once
#include <vector>
#include <memory>
#include <mutex>
#include <string>
#include <functional>

namespace transfer::core {
    class Session;

    // Defines the states of the file transfer process for upper-layer callbacks.
    enum class TransferState {
        STARTED = 0,
        PROGRESS = 1,
        COMPLETED = 2,
        CONNECTED = 3,
        ERROR = -1
    };

    // Callback format: (FileName, State, ProgressPercentage, Message)
    using TransferCallback = std::function<void(const std::string&, TransferState, int, const std::string&)>;

    class SessionManager {
    public:
        // Adds a newly connected session (pipe) to the manager.
        void add_session(std::shared_ptr<Session> session);

        // Initiates parallel file transfer using all currently active sessions.
        void push_to_single_peer(const std::string& file_path);

        // Called by individual sessions when their assigned chunk transfer is finished or failed.
        // is_success: true if the chunk was transferred without network errors.
        void notify_transfer_finished(bool is_success = true);

        void notify_transfer_error(const std::string& file_name, const std::string& error_msg);

        // Injects the callback function to communicate with the High-level layer.
        void set_callback(TransferCallback callback) { callback_ = callback; }
        TransferCallback get_callback() const { return callback_; }

    private:
        // Container for all parallel pipes connected to the single peer.
        std::vector<std::shared_ptr<Session>> sessions_;
        std::mutex mutex_;

        std::string active_file_path_;
        bool is_broadcasting_ = false;

        // Tracks the number of pipes currently transmitting data.
        int active_transfers_count_ = 0;

        TransferCallback callback_;

        // Flag to prevent emitting a SUCCESS callback if any of the parallel pipes failed.
        bool has_error_ = false;
    };
} // namespace transfer::core