#include "PeerNode.hpp"
#include <iostream>
#include "Logger.hpp"

namespace transfer::core {
    PeerNode::PeerNode() : io_context_(),
                           work_guard_(asio::make_work_guard(io_context_)),
                           acceptor_(io_context_),
                           socket_(io_context_) { // socket_ 초기화
    }

    PeerNode::~PeerNode() {
        stop();
    }

    // SessionManager 관련 콜백 설정은 모두 제거됩니다.
    // void PeerNode::set_transfer_callback(TransferCallback callback) { ... }
    // void PeerNode::set_fd_request_callback(FdRequestCallback callback) { ... }
    // void PeerNode::set_encryption_key(const std::string &key) { ... }

    void PeerNode::start() {
        if (worker_thread_.joinable()) return;

        io_context_.restart();
        worker_thread_ = std::thread([this]() {
            try {
                io_context_.run();
            } catch (const std::exception &e) {
                LOGE("[PeerNode] Critical Engine Error: %s", e.what());
            }
        });
        LOGI("[PeerNode] ASIO Event Loop started.");
    }

    void PeerNode::stop() {
        if (acceptor_.is_open()) {
            asio::error_code ec;
            acceptor_.close(ec);
        }

        work_guard_.reset();

        if (!io_context_.stopped()) {
            io_context_.stop();
        }

        if (worker_thread_.joinable()) {
            worker_thread_.join();
        }
        LOGI("[PeerNode] Engine shut down.");
    }

    bool PeerNode::startReceiver(uint16_t port) {
        try {
            asio::ip::tcp::endpoint endpoint(asio::ip::tcp::v4(), port);
            acceptor_.open(endpoint.protocol());
            acceptor_.set_option(asio::ip::tcp::acceptor::reuse_address(true));
            acceptor_.bind(endpoint);
            acceptor_.listen();

            LOGI("[PeerNode] Listening on port: %d", port);
            do_accept();
            return true;
        } catch (const std::exception &e) {
            LOGE("[PeerNode] Failed to start listening: %s", e.what());
            return false;
        }
    }

    void PeerNode::do_accept() {
        acceptor_.async_accept([this](std::error_code ec, asio::ip::tcp::socket socket) {
            if (!ec) {
                try {
                    socket.set_option(asio::ip::tcp::no_delay(true));
                    socket.set_option(asio::socket_base::receive_buffer_size(TCP_BUFFER_SIZE));

                    LOGI("[PeerNode] Peer connected. Delegating raw socket to upper layer.");

                    if (connection_handler_) {
                        auto socket_ptr = std::make_shared<asio::ip::tcp::socket>(std::move(socket));
                        connection_handler_(socket_ptr);
                    }
                } catch (const std::exception &e) {
                    LOGE("[PeerNode] Socket tuning failed: %s", e.what());
                }
            } else {
                if (ec != asio::error::operation_aborted) {
                    LOGE("[PeerNode] Accept failed: %s", ec.message().c_str());
                }
            }
            // 단일 연결만 처리하므로 재귀 호출을 제거합니다.
            // if (acceptor_.is_open()) {
            //     do_accept();
            // }
        });
    }

    void PeerNode::startSender(const std::string &ip, uint16_t port) {
        LOGI("[PeerNode] Initiating connection to %s:%d", ip.c_str(), port);
        auto endpoint = asio::ip::tcp::endpoint(asio::ip::make_address(ip), port);
        do_connect(endpoint);
    }

    void PeerNode::do_connect(const asio::ip::tcp::endpoint& endpoint) {
        socket_.async_connect(endpoint, [this](std::error_code ec) {
            if (!ec) {
                try {
                    socket_.set_option(asio::ip::tcp::no_delay(true));
                    socket_.set_option(asio::socket_base::send_buffer_size(TCP_BUFFER_SIZE));

                    LOGI("[PeerNode] Pipe connected. Delegating raw socket.");

                    if (connection_handler_) {
                        // socket_을 std::move를 사용하여 소유권을 이전합니다.
                        connection_handler_(std::make_shared<asio::ip::tcp::socket>(std::move(socket_)));
                    }
                } catch (const std::exception &e) {
                    LOGE("[PeerNode] Socket tuning error: %s", e.what());
                }
            } else {
                LOGE("[PeerNode] Connection failed: %s", ec.message().c_str());
                // 실패 콜백은 상위 레이어에서 처리하도록 합니다.
            }
        });
    }
} // namespace transfer::core