#include "SessionManager.hpp"
#include "Session.hpp" // Include the full definition of Session
#include "Logger.hpp"
#include <mutex>

namespace transfer::core {

SessionManager::SessionManager(asio::io_context& io_context)
    : io_context_(io_context) {
    LOGI("SessionManager created.");
}

SessionManager::~SessionManager() {
    // Destructor implementation can be added if needed.
}

void SessionManager::startSend(std::shared_ptr<asio::ip::tcp::socket> socket, const std::string& file_path) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (session_) {
        LOGW("A session is already active. Please stop it before starting a new one.");
        return;
    }

    LOGI("Creating and starting a new send session for file: %s", file_path.c_str());
    session_ = std::make_shared<Session>(io_context_, std::move(socket));

    session_->set_status_callback([this, file_path](TransferState state, int progress, const std::string& message) {
        if (callback_) {
            callback_(file_path, state, progress, message);
        }
        if (state == TransferState::COMPLETED || state == TransferState::ERROR) {
            std::lock_guard<std::mutex> lock(mutex_);
            session_.reset();
            LOGI("Session finished and has been reset.");
        }
    });
    session_->set_fd_request_callback(fd_callback_);

    session_->startSend(file_path);
}

void SessionManager::startReceive(std::shared_ptr<asio::ip::tcp::socket> socket, const std::string& save_path) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (session_) {
        LOGW("A session is already active. Please stop it before starting a new one.");
        return;
    }

    LOGI("Creating and starting a new receive session, saving to: %s", save_path.c_str());
    session_ = std::make_shared<Session>(io_context_, std::move(socket));

    session_->set_status_callback([this](TransferState state, int progress, const std::string& message) {
        std::string file_name = "receiving_file"; // TODO: Get filename from session
        if (callback_) {
            callback_(file_name, state, progress, message);
        }
        if (state == TransferState::COMPLETED || state == TransferState::ERROR) {
            std::lock_guard<std::mutex> lock(mutex_);
            session_.reset();
            LOGI("Session finished and has been reset.");
        }
    });
    session_->set_fd_request_callback(fd_callback_);

    session_->startReceive(save_path);
}

void SessionManager::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (session_) {
        LOGI("Stopping the active session.");
        session_->stop();
        session_.reset();
    }
}

} // namespace transfer::core