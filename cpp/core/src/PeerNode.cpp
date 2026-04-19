#include "PeerNode.hpp"
#include "Session.hpp"
#include <iostream>

namespace transfer::core {

    PeerNode::PeerNode() : io_context_(),
                           work_guard_(asio::make_work_guard(io_context_)),
                           acceptor_(io_context_),
                           session_manager_(std::make_shared<SessionManager>()) {
    }

    PeerNode::~PeerNode() {
        stop();
    }

    void PeerNode::set_transfer_callback(TransferCallback callback) {
        callback_ = callback;
        session_manager_->set_callback(callback);
    }

    void PeerNode::start() {
        // Prevent multiple thread executions
        if (worker_thread_.joinable()) return;

        io_context_.restart();
        worker_thread_ = std::thread([this]() {
            try {
                io_context_.run();
            }
            catch (const std::exception &e) {
                LOGE("[PeerNode] Engine Error: %s", e.what());
            }
        });
    }

    void PeerNode::stop() {
        work_guard_.reset();

        if (!io_context_.stopped()) {
            io_context_.stop();
        }

        if (worker_thread_.joinable()) {
            worker_thread_.join();
        }
    }

    bool PeerNode::start_listening(uint16_t port) {
        try {
            asio::ip::tcp::endpoint endpoint(asio::ip::tcp::v4(), port);
            acceptor_.open(endpoint.protocol());

            // Allow immediate port reuse after termination (ignores TIME_WAIT state)
            acceptor_.set_option(asio::ip::tcp::acceptor::reuse_address(true));
            acceptor_.bind(endpoint);
            acceptor_.listen();

            // Start waiting for the first incoming connection
            do_accept();
            return true;
        } catch (const std::exception &e) {
            LOGE("[Hub] Listen error: %s", e.what());
            if (callback_) callback_("N/A", TransferState::ERROR, 0, e.what());
            return false;
        }
    }

    void PeerNode::do_accept() {
        acceptor_.async_accept([this](std::error_code ec, asio::ip::tcp::socket socket) {
            if (!ec) {
                 try {
                     socket.set_option(asio::ip::tcp::no_delay(true));
                     // Expand the receive buffer to prevent bottlenecks on the Hub side
                     socket.set_option(asio::socket_base::receive_buffer_size(8 * 1024 * 1024));

                     auto session = std::make_shared<Session>(std::move(socket), session_manager_);
                     session_manager_->add_session(session);

                     // Immediately start waiting for incoming data headers
                     session->start_receive_loop();
                 } catch (const std::exception& e) {
                     LOGE("[Hub] Session creation error: %s", e.what());
                 }
             } else {
                 // Log the accept error to prevent silent failures
                 LOGE("[Hub] Accept failed: %s", ec.message().c_str());
             }

            // Continue accepting next connections as long as the acceptor is open
            if (acceptor_.is_open()) {
                do_accept();
            }
        });
    }

    void PeerNode::connect_to_peer(const std::string &ip, uint16_t port, int session_count) {
        for (int i = 0; i < session_count; ++i) {
            auto socket = std::make_shared<asio::ip::tcp::socket>(io_context_);
            asio::ip::tcp::endpoint endpoint(asio::ip::make_address(ip), port);

            // Execute asynchronous connection attempt
            socket->async_connect(endpoint, [this, socket, i, ip](std::error_code ec) {
                if (!ec) {
                    try {
                        // Disable Nagle's algorithm for instant packet delivery
                        socket->set_option(asio::ip::tcp::no_delay(true));
                        // Tuning: Expand the send buffer to 8MB to maximize mobile network throughput
                        socket->set_option(asio::socket_base::send_buffer_size(8 * 1024 * 1024));

                        // Move the connected socket into a new Session instance
                        auto session = std::make_shared<Session>(std::move(*socket), session_manager_);
                        session_manager_->add_session(session);
                        session->start_receive_loop();

                        if (i == 0 && callback_) {
                            callback_("SYSTEM", TransferState::CONNECTED, 0, "Connected to " + ip);
                        }
                    } catch (const std::exception& e) {
                        LOGE("[Spoke] Connection tuning error: %s", e.what());
                    }
                } else {
                    LOGE("[Spoke] Failed to connect pipe %d : %s", i, ec.message().c_str());
                    if (callback_) callback_("N/A", TransferState::ERROR, 0, "Connect failed: " + ec.message());
                }
            });
        }
    }

    void PeerNode::send_file(const std::string &file_path) {
        // Ensure thread safety by posting the task to the ASIO worker thread
        asio::post(io_context_, [this, file_path]() {
            if (session_manager_) {
                session_manager_->push_to_single_peer(file_path);
            }
        });
    }

} // namespace transfer::core