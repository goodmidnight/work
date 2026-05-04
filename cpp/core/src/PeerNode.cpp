#include "PeerNode.hpp"
#include <iostream>
#include "Logger.hpp"

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
        transfer_callback_ = callback;
        session_manager_->set_callback(callback);
    }

    void PeerNode::set_fd_request_callback(FdRequestCallback callback) {
        session_manager_->set_fd_callback(callback);
    }

    void PeerNode::set_encryption_key(const std::string &key) {
        session_manager_->set_encryption_key(key);
        LOGI("[PeerNode] E2E Encryption Key has been securely loaded.");
    }

    void PeerNode::start() {
        // Prevent launching multiple ASIO event loops simultaneously
        if (worker_thread_.joinable()) return;

        io_context_.restart();
        worker_thread_ = std::thread([this]() {
            try {
                // Blocks the thread to process asynchronous networking events
                io_context_.run();
            } catch (const std::exception &e) {
                LOGE("[PeerNode] Critical Engine Error: %s", e.what());
            }
        });
        LOGI("[PeerNode] ASIO Event Loop started.");
    }

    void PeerNode::stop() {
        // Close the acceptor to reject any new incoming connection attempts
        if (acceptor_.is_open()) {
            asio::error_code ec;
            acceptor_.close(ec);
        }

        // Release the work guard to allow the io_context to exit when tasks run out
        work_guard_.reset();

        if (!io_context_.stopped()) {
            io_context_.stop();
        }

        // Wait for the background worker thread to finish execution safely
        if (worker_thread_.joinable()) {
            worker_thread_.join();
        }
        LOGI("[PeerNode] Engine shut down gracefully.");
    }

    bool PeerNode::startReceiver(uint16_t port) {
        try {
            asio::ip::tcp::endpoint endpoint(asio::ip::tcp::v4(), port);
            acceptor_.open(endpoint.protocol());

            // SO_REUSEADDR: Allows immediate rebinding to the port, preventing TIME_WAIT blocking errors
            acceptor_.set_option(asio::ip::tcp::acceptor::reuse_address(true));
            acceptor_.bind(endpoint);
            acceptor_.listen();

            LOGI("[Hub] Listening on port: %d", port);
            do_accept();
            return true;
        } catch (const std::exception &e) {
            LOGE("[Hub] Failed to start listening: %s", e.what());
            return false;
        }
    }

    void PeerNode::do_accept() {
        // Asynchronously wait for a client to connect
        acceptor_.async_accept([this](std::error_code ec, asio::ip::tcp::socket socket) {
            if (!ec) {
                try {
                    // Disable Nagle's algorithm for low-latency transmission of control packets
                    socket.set_option(asio::ip::tcp::no_delay(true));

                    // Expand receive buffer to prevent network bottlenecks during high-speed transfers
                    socket.set_option(asio::socket_base::receive_buffer_size(TCP_BUFFER_SIZE));

                    LOGI("[Hub] Peer connected. Delegating raw socket to upper layer.");

                    // Transfer socket ownership to the upper Facade layer (TransferEngine) for Session assembly
                    if (connection_handler_) {
                        auto socket_ptr = std::make_shared<asio::ip::tcp::socket>(std::move(socket));
                        connection_handler_(socket_ptr);
                    }
                } catch (const std::exception &e) {
                    LOGE("[Hub] Socket tuning failed: %s", e.what());
                }
            } else {
                if (ec != asio::error::operation_aborted) {
                    LOGE("[Hub] Accept failed: %s", ec.message().c_str());
                }
            }

            // Loop recursively to accept subsequent incoming connections (for multi-pipe support)
            if (acceptor_.is_open()) {
                do_accept();
            }
        });
    }

    void PeerNode::startSender(const std::string &ip, uint16_t port, int session_count) {
        LOGI("[Spoke] Initiating %d parallel connections to %s:%d", session_count, ip.c_str(), port);

        // Establish multiple parallel TCP connections (Pipes) for maximum throughput
        for (int i = 0; i < session_count; ++i) {
            auto socket = std::make_shared<asio::ip::tcp::socket>(io_context_);
            auto endpoint = asio::ip::tcp::endpoint(asio::ip::make_address(ip), port);

            // Asynchronously connect to the target endpoint
            socket->async_connect(endpoint, [this, socket, i, ip](std::error_code ec) {
                if (!ec) {
                    try {
                        // Apply socket performance tuning
                        socket->set_option(asio::ip::tcp::no_delay(true));
                        socket->set_option(asio::socket_base::send_buffer_size(TCP_BUFFER_SIZE));

                        LOGI("[Spoke] Pipe %d connected. Delegating raw socket.", i);

                        // Transfer socket ownership to the Facade layer
                        if (connection_handler_) {
                            connection_handler_(socket);
                        }

                        // Notify the UI layer only once upon the first successful pipe connection
                        if (i == 0 && transfer_callback_) {
                            transfer_callback_("SYSTEM", TransferState::CONNECTED, 0, "Connected to " + ip);
                        }
                    } catch (const std::exception &e) {
                        LOGE("[Spoke] Socket tuning error on pipe %d: %s", i, e.what());
                    }
                } else {
                    LOGE("[Spoke] Connection failed on pipe %d: %s", i, ec.message().c_str());
                    if (i == 0 && transfer_callback_) {
                        transfer_callback_("N/A", TransferState::ERROR, 0, "Connection failed: " + ec.message());
                    }
                }
            });
        }
    }

    void PeerNode::pushFile(const std::string &file_path) {
        // asio::post ensures thread safety by executing the block within the ASIO context thread
        asio::post(io_context_, [this, file_path]() {
            if (session_manager_) {
                session_manager_->push_to_single_peer(file_path);
            }
        });
    }
} // namespace transfer::core
