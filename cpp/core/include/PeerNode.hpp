#pragma once

#include <asio.hpp>
#include <memory>
#include <string>
#include <thread>
#include <functional>

namespace transfer::core {

    using FdRequestCallback = std::function<int(const std::string &)>;
    using ConnectionHandler = std::function<void(std::shared_ptr<asio::ip::tcp::socket>)>;

    class PeerNode {
    public:
        PeerNode();
        ~PeerNode();

        void start();
        void stop();
        bool startReceiver(uint16_t port);
        void startSender(const std::string &ip, uint16_t port);

        void set_connection_handler(ConnectionHandler handler) {
            connection_handler_ = std::move(handler);
        }

        /**
         * @brief Provides access to the underlying ASIO I/O context.
         * @return A reference to the io_context.
         */
        asio::io_context& get_io_context() { return io_context_; }

    private:
        void do_accept();
        void do_connect(const asio::ip::tcp::endpoint& endpoint);

        static constexpr size_t TCP_BUFFER_SIZE = 8 * 1024 * 1024;

        asio::io_context io_context_;
        asio::executor_work_guard<asio::io_context::executor_type> work_guard_;
        std::thread worker_thread_;
        asio::ip::tcp::acceptor acceptor_;
        asio::ip::tcp::socket socket_;

        ConnectionHandler connection_handler_;
    };

} // namespace transfer::core