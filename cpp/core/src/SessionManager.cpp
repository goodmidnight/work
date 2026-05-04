#include "SessionManager.hpp"
#include "Session.hpp" // Required to call start_push_range on Session objects[cite: 15]
#include <filesystem>
#include "Logger.hpp"

namespace transfer::core {

    void SessionManager::add_session(std::shared_ptr<Session> session) {
        // Ensure thread-safe access to the sessions pool, as multiple sockets might connect concurrently.[cite: 15]
        std::lock_guard<std::mutex> lock(mutex_);
        sessions_.push_back(session);
        LOGI("[Manager] Session added. (Total pipes: %zu)", sessions_.size());
    }

    void SessionManager::push_to_single_peer(const std::string& file_path) {
        std::lock_guard<std::mutex> lock(mutex_);

        // Abort if the user attempts to send a file before any network pipes are established.[cite: 15]
        if (sessions_.empty()) {
            if (callback_) callback_(file_path, TransferState::ERROR, 0, "No active sessions.");
            return;
        }

        // Initialize state variables for a new file broadcast.[cite: 15]
        active_file_path_ = file_path;
        is_broadcasting_ = true;
        has_error_ = false;

        // Query the underlying OS filesystem to determine the exact total size of the file.[cite: 15]
        std::error_code ec;
        uint64_t total_size = std::filesystem::file_size(file_path, ec);
        if (ec) {
            LOGE("[Manager] Failed to read file size: %s", ec.message().c_str());
            if (callback_) callback_(file_path, TransferState::ERROR, 0, "File read error.");
            return;
        }

        int n = sessions_.size();

        // Core Algorithm: Divide the total file size equally among the available pipes (e.g., 4 pipes).[cite: 15]
        uint64_t chunk_size = total_size / n;
        active_transfers_count_ = 0;

        bool is_encrypted = !encryption_key_.empty();
        LOGI("[Manager] Starting parallel transfer. File: %s, Size: %llu, Pipes: %d, Encrypted: %s",
             file_path.c_str(), total_size, n, is_encrypted ? "YES" : "NO");

        // Notify the upper UI layer that the transfer process has officially begun.[cite: 15]
        if (callback_) callback_(file_path, TransferState::STARTED, 0, "Transfer started.");

        // Dispatch specific byte ranges to each individual session pipe.[cite: 15]
        for (int i = 0; i < n; ++i) {
            uint64_t offset = i * chunk_size;

            // Critical: The last session takes all remaining bytes.
            // This prevents truncation errors caused by integer division remainders (e.g., 100 / 3).[cite: 15]
            uint64_t length = (i == n - 1) ? (total_size - offset) : chunk_size;

            active_transfers_count_++; // Increment the tracking latch for each dispatched task.[cite: 15]

            // Instruct the session to begin pushing its assigned chunk independently.[cite: 15]
            sessions_[i]->start_push_range(file_path, offset, length, i);
        }
    }

    void SessionManager::notify_transfer_finished(bool is_success) {
        std::lock_guard<std::mutex> lock(mutex_);

        // Ignore stray or delayed callbacks if a transfer isn't actively being tracked.[cite: 15]
        if (!is_broadcasting_) return;

        // If any single pipe fails, mark the entire global file transfer as compromised.[cite: 15]
        if (!is_success) {
            has_error_ = true;
        }

        // Latch mechanism: Decrement the active task counter.
        // We must wait until ALL parallel pipes have reported back (either success or fail).[cite: 15]
        if (--active_transfers_count_ <= 0) {
            is_broadcasting_ = false;

            // Evaluate the final aggregated state of the file transfer.[cite: 15]
            if (has_error_) {
                LOGI("[Manager] Transfer finished with ERRORS. Closing compromised pipes.");
            } else {
                LOGI("[Manager] All parallel chunks completed successfully.");
                if (callback_) callback_(active_file_path_, TransferState::COMPLETED, 100, "Success");
            }

            // Flush the session pool.
            // By design, single-file broadcast architecture requires new connections for subsequent files
            // to prevent desynchronization and ensure a clean state.[cite: 15]
            sessions_.clear();
            has_error_ = false;
        }
    }

    void SessionManager::notify_transfer_error(const std::string& file_name, const std::string& error_msg) {
        std::lock_guard<std::mutex> lock(mutex_);

        // Ensure the error callback is fired only ONCE per broadcast.
        // This prevents spamming the UI thread if multiple pipes fail simultaneously (e.g., router disconnects).[cite: 15]
        if (!has_error_) {
            has_error_ = true;
            LOGE("[Manager] Transfer failed: %s", error_msg.c_str());
            if (callback_) {
                callback_(file_name, TransferState::ERROR, 0, error_msg);
            }
        }
    }

} // namespace transfer::core