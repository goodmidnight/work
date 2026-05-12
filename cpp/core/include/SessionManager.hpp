#pragma once

#include <memory>
#include <string>
#include <functional>
#include <asio/io_context.hpp>
#include <asio/ip/tcp.hpp>
#include <mutex>
#include <system_error>
#include "Types.hpp"
#include "Session.hpp"

namespace transfer::core {

    class SessionManager {
    public:
        SessionManager(asio::io_context& io_context, ConnectFn connect_fn);
        ~SessionManager();

        void startSend(std::shared_ptr<asio::ip::tcp::socket> control_socket, const std::string& file_path, const std::string& target_ip, uint16_t target_port, int session_count);

        void startReceive(std::shared_ptr<asio::ip::tcp::socket> control_socket, const std::string& save_path);

        // Handles incoming connections. Receives an error code from the network layer.
        void handleIncomingSocket(std::shared_ptr<asio::ip::tcp::socket> socket, std::error_code ec);

        void stop();

        // --- Dependency Injections ---
        void set_callback(TransferCallback cb) { callback_ = std::move(cb); }
        void set_fd_callback(FdRequestCallback cb) { fd_callback_ = std::move(cb); }
        void set_encryption_key(const std::string& key) { encryption_key_ = key; }
        void set_save_path(const std::string& path) { save_path_ = path; }

    private:
        asio::io_context& io_context_;
        ConnectFn connect_fn_;

        std::shared_ptr<Session> session_;
        std::mutex mutex_;

        TransferCallback callback_;
        FdRequestCallback fd_callback_;
        std::string encryption_key_;
        std::string save_path_;
    };

} // namespace transfer::core