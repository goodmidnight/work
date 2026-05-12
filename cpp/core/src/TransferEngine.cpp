#include "TransferEngine.hpp"
#include "PeerNode.hpp"
#include "Logger.hpp"

namespace transfer::core {

TransferEngine::TransferEngine() : peer_node_(std::make_unique<PeerNode>()) {
    // After the PeerNode is created, use its io_context to initialize the SessionManager.
    session_manager_ = std::make_shared<SessionManager>(peer_node_->get_io_context());

    // Set up the connection handler that PeerNode will invoke when a TCP connection is made.
    // This is the crucial link between the network layer (PeerNode) and the session layer (SessionManager).
    peer_node_->set_connection_handler([this](std::shared_ptr<asio::ip::tcp::socket> socket) {
        if (!file_to_send_.empty()) {
            // If the engine is in 'sender' mode, start a send session.
            LOGI("Connection established. Starting send session for: %s", file_to_send_.c_str());
            session_manager_->startSend(std::move(socket), file_to_send_);
            // Clear the state after initiating the session.
            file_to_send_.clear();
        } else if (!save_path_.empty()) {
            // If the engine is in 'receiver' mode, start a receive session.
            LOGI("Connection accepted. Starting receive session to: %s", save_path_.c_str());
            session_manager_->startReceive(std::move(socket), save_path_);
            // The save_path could be kept for receiving multiple files, but for now,
            // we assume a single file transfer per connection.
        } else {
            // This case should ideally not happen in a well-behaved client.
            LOGE("Connection received, but no mode (send/receive) is set!");
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
    // Delegate the callback to the SessionManager, which reports transfer status.
    session_manager_->set_callback(callback);
}

void TransferEngine::set_fd_request_callback(const FdRequestCallback &callback) {
    // Delegate the file descriptor request callback, used for platform-specific file access (e.g., Android SAF).
    session_manager_->set_fd_callback(callback);
}

void TransferEngine::set_encryption_key(const std::string &key) {
    // Pass the encryption key down to the session layer.
    session_manager_->set_encryption_key(key);
}

// --- Control Methods ---

bool TransferEngine::startReceiver(uint16_t port, const std::string& save_path) {
    // Set the engine to receiver mode.
    file_to_send_.clear();
    save_path_ = save_path;
    // Start the underlying network worker thread.
    peer_node_->start();
    // Begin listening for incoming connections.
    LOGI("Starting receiver on port %d, saving to %s", port, save_path.c_str());
    return peer_node_->startReceiver(port);
}

void TransferEngine::startSender(const std::string& ip, uint16_t port, const std::string& file_path) {
    // Set the engine to sender mode.
    save_path_.clear();
    file_to_send_ = file_path;
    // Start the underlying network worker thread.
    peer_node_->start();
    // Initiate a connection to the receiver.
    LOGI("Starting sender to %s:%d for file %s", ip.c_str(), port, file_path.c_str());
    peer_node_->startSender(ip, port);
}

void TransferEngine::stop() {
    LOGI("Stopping TransferEngine...");
    // Stop the session layer first to gracefully end any ongoing transfers.
    if (session_manager_) {
        session_manager_->stop();
    }
    // Stop the network layer to shut down the worker thread and close sockets.
    if (peer_node_) {
        peer_node_->stop();
    }
    LOGI("TransferEngine stopped.");
}

} // namespace transfer::core