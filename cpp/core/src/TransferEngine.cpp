#include "TransferEngine.hpp"
#include "PeerNode.hpp"
#include "Logger.hpp"

namespace transfer::core {

TransferEngine::TransferEngine() : peer_node_(std::make_unique<PeerNode>()) {
    // We need to pass a ConnectFn to SessionManager so it can open Data Channels.
    auto connect_fn = [this](const std::string& ip, uint16_t port, ConnectionHandler handler) {
        peer_node_->connect(ip, port, std::move(handler));
    };

    session_manager_ = std::make_shared<SessionManager>(peer_node_->get_io_context(), connect_fn);

    // Set up the connection handler that PeerNode will invoke when a TCP connection is accepted (Receiver side).
    peer_node_->set_connection_handler([this](std::shared_ptr<asio::ip::tcp::socket> socket) {
        if (!save_path_.empty()) {
            LOGI("Connection accepted. Delegating to SessionManager for: %s", save_path_.c_str());
            session_manager_->handleIncomingSocket(std::move(socket));
        } else {
            LOGE("Connection received, but receiver mode is not set properly!");
            socket->close();
        }
    });
    LOGI("TransferEngine initialized.");
}

TransferEngine::~TransferEngine() {
    stop();
}

// --- Configuration ---

void TransferEngine::set_transfer_callback(const TransferCallback &callback) {
    session_manager_->set_callback(callback);
}

void TransferEngine::set_fd_request_callback(const FdRequestCallback &callback) {
    session_manager_->set_fd_callback(callback);
}

void TransferEngine::set_encryption_key(const std::string &key) {
    session_manager_->set_encryption_key(key);
}

// --- Control Methods ---

bool TransferEngine::startReceiver(uint16_t port, const std::string& save_path) {
    file_to_send_.clear();
    save_path_ = save_path;
    // Tell the SessionManager what the save path is, so it can handle the first socket properly.
    session_manager_->set_save_path(save_path);
    peer_node_->start();
    LOGI("Starting receiver on port %d, saving to %s", port, save_path.c_str());
    return peer_node_->startReceiver(port);
}

void TransferEngine::startSender(const std::string& ip, uint16_t port, const std::string& file_path) {
    save_path_.clear();
    file_to_send_ = file_path;
    peer_node_->start();

    LOGI("Starting sender to %s:%d for file %s", ip.c_str(), port, file_path.c_str());

    peer_node_->connect(ip, port, [this, ip, port, file_path](std::shared_ptr<asio::ip::tcp::socket> socket) {
        LOGI("Control connection established. Starting send session for: %s", file_path.c_str());
        // Default to 4 sessions for now, can be configured later.
        session_manager_->startSend(std::move(socket), file_path, ip, port, 4);
    });
}

void TransferEngine::stop() {
    LOGI("Stopping TransferEngine...");
    if (session_manager_) {
        session_manager_->stop();
    }
    if (peer_node_) {
        peer_node_->stop();
    }
    LOGI("TransferEngine stopped.");
}

} // namespace transfer::core