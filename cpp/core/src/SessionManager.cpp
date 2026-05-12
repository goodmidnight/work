#include "SessionManager.hpp"
#include "Logger.hpp"

namespace transfer::core {

SessionManager::SessionManager(asio::io_context& io_context, ConnectFn connect_fn)
    : io_context_(io_context), connect_fn_(std::move(connect_fn)) {
    LOGI("SessionManager created.");
}

SessionManager::~SessionManager() {
}

void SessionManager::startSend(std::shared_ptr<asio::ip::tcp::socket> socket, const std::string& file_path, const std::string& ip, uint16_t port, int session_count) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (session_) {
        LOGW("A session is already active. Please stop it before starting a new one.");
        return;
    }

    LOGI("Creating and starting a new send session for file: %s", file_path.c_str());
    session_ = std::make_shared<Session>(io_context_, std::move(socket), connect_fn_);

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

    session_->startSend(file_path, ip, port, session_count);
}

void SessionManager::startReceive(std::shared_ptr<asio::ip::tcp::socket> socket, const std::string& save_path) {
    // We assume the caller holds the mutex or it's safe to run (called from handleIncomingSocket)
    LOGI("Creating and starting a new receive session, saving to: %s", save_path.c_str());
    session_ = std::make_shared<Session>(io_context_, std::move(socket), connect_fn_);

    session_->set_status_callback([this](TransferState state, int progress, const std::string& message) {
        std::string file_name = "receiving_file";
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

void SessionManager::handleIncomingSocket(std::shared_ptr<asio::ip::tcp::socket> socket) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (session_) {
        // If there's an active session, this must be a Data Channel.
        LOGI("New socket connected. Delegating to active session as Data Channel.");
        session_->addDataSocket(std::move(socket));
    } else {
        // If there's no active session, but we have a save_path configured, start a new receive session.
        if (!save_path_.empty()) {
            LOGI("New socket connected. Treating as Control Channel.");
            startReceive(std::move(socket), save_path_);
        } else {
            LOGE("Received socket, but no session is active and no save_path is configured.");
            asio::error_code ec;
            socket->close(ec);
        }
    }
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