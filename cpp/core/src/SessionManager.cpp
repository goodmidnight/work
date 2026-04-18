#include "SessionManager.hpp"
#include "Session.hpp"
#include <iostream>
#include <filesystem>

#include "PeerNode.hpp"

namespace transfer::core {

    void SessionManager::add_session(std::shared_ptr<Session> session) {
        std::lock_guard<std::mutex> lock(mutex_);
        sessions_.push_back(session);
        LOGI("[Manager] Session added. (Total pipes: %zu)", sessions_.size());
    }

    void SessionManager::push_to_single_peer(const std::string& file_path) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (sessions_.empty()) {
            if (callback_) callback_(file_path, TransferState::ERROR, 0, "No active sessions.");
            return;
        }

        active_file_path_ = file_path;
        is_broadcasting_ = true;

        std::error_code ec;
        uint64_t total_size = std::filesystem::file_size(file_path, ec);
        if (ec) {
            if (callback_) callback_(file_path, TransferState::ERROR, 0, "File read error.");
            return;
        }

        int n = sessions_.size();
        uint64_t chunk_size = total_size / n;
        active_transfers_count_ = 0;

        if (callback_) callback_(file_path, TransferState::STARTED, 0, "Transfer started.");

        // Distribute the file size evenly across all available sessions.
        for (int i = 0; i < n; ++i) {
            uint64_t offset = i * chunk_size;
            // The last session handles any remaining bytes to prevent truncation errors.
            uint64_t length = (i == n - 1) ? (total_size - offset) : chunk_size;

            active_transfers_count_++;
            sessions_[i]->start_push_range(file_path, offset, length, i);
        }
    }

    void SessionManager::notify_transfer_finished(bool is_success) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!is_broadcasting_) return;

        // Mark the entire transfer as failed if any single pipe reports an error.
        if (!is_success) {
            has_error_ = true;
        }

        // Decrement the active pipe counter.
        if (--active_transfers_count_ <= 0) {
            is_broadcasting_ = false;

            // Evaluate the overall result only after all pipes have stopped.
            if (has_error_) {
                LOGI("[Manager] Transfer finished with ERRORS.");
            } else {
                LOGI("[Manager] All parallel chunks completed successfully.");
                if (callback_) callback_(active_file_path_, TransferState::COMPLETED, 100, "Success");
            }

            // Clean up resources for the next transfer operation.
            sessions_.clear();
            has_error_ = false;
        }
    }

    // SessionManager.cpp 구현
    void SessionManager::notify_transfer_error(const std::string& file_name, const std::string& error_msg) {
        std::lock_guard<std::mutex> lock(mutex_);

        if (!has_error_) {
            has_error_ = true;
            LOGE("[Manager] Transfer failed: %s", error_msg.c_str());
            if (callback_) {
                callback_(file_name, TransferState::ERROR, 0, error_msg);
            }
        }
    }

} // namespace transfer::core