#include "TransferEngine.hpp"
#include "PeerNode.hpp"
#include "Session.hpp"
#include "Logger.hpp"

namespace transfer::core {

    TransferEngine::TransferEngine() 
        : peer_node_(std::make_unique<PeerNode>()),
          session_manager_(std::make_shared<SessionManager>()) {
          
        peer_node_->set_connection_handler([this](asio::ip::tcp::socket socket) {
            auto session = std::make_shared<Session>(std::move(socket), session_manager_);
            session_manager_->add_session(session);
            session->start_receive_loop();
        });
    }

    TransferEngine::~TransferEngine() {
        stop();
    }

    void TransferEngine::set_transfer_callback(const TransferCallback &callback) const {
        session_manager_->set_callback(callback);
    }

    void TransferEngine::set_fd_request_callback(const FdRequestCallback &callback) const {
        session_manager_->set_fd_callback(callback);
    }

    void TransferEngine::set_encryption_key(const std::string& key) const {
        session_manager_->set_encryption_key(key);
        LOGI("[TransferEngine] E2E Encryption Key loaded.");
    }

    bool TransferEngine::startReceiver(const uint16_t port) const {
        peer_node_->start();
        return peer_node_->startReceiver(port);
    }

    void TransferEngine::startSender(const std::string& ip, uint16_t port, int session_count) const {
        peer_node_->start();
        peer_node_->startSender(ip, port, session_count);
    }

    void TransferEngine::send_file(const std::string& file_path) const {
        peer_node_->send_file(file_path);
    }

    void TransferEngine::stop() const {
        peer_node_->stop();
    }

} // namespace transfer::core