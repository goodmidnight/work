#pragma once

#include <memory>
#include <string>
#include <functional>
#include <asio/io_context.hpp>
#include <asio/ip/tcp.hpp>
#include <mutex>
#include "Types.hpp" // Include the new types header

namespace transfer::core {

    // Forward-declare Session to break the circular dependency.
    class Session;

    /**
     * @class SessionManager
     * @brief Manages the lifecycle of a single transfer session and reports its status.
     *        It acts as a bridge between the network session and the application layer (e.g., JNI).
     */
    class SessionManager {
    public:
        SessionManager(asio::io_context& io_context);
        ~SessionManager();

        void startSend(std::shared_ptr<asio::ip::tcp::socket> socket, const std::string& file_path);
        void startReceive(std::shared_ptr<asio::ip::tcp::socket> socket, const std::string& save_path);
        void stop();

        // --- Dependency Injections ---
        void set_callback(TransferCallback cb) { callback_ = std::move(cb); }
        void set_fd_callback(FdRequestCallback cb) { fd_callback_ = std::move(cb); }
        void set_encryption_key(const std::string& key) { encryption_key_ = key; }

    private:
        asio::io_context& io_context_;
        std::shared_ptr<Session> session_;
        std::mutex mutex_;

        TransferCallback callback_;
        FdRequestCallback fd_callback_;
        std::string encryption_key_;
    };

} // namespace transfer::core