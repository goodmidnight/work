#pragma once

#include <asio.hpp>
#include <memory>
#include <string>
#include <thread>
#include <functional>
#include "SessionManager.hpp"

namespace transfer::core {

    /**
     * @brief Callback type for requesting a File Descriptor from the host platform (e.g., Android SAF, iOS).
     */
    using FdRequestCallback = std::function<int(const std::string &)>;

    /**
     * @brief Callback type to delegate the successfully connected raw socket to the upper layer.
     */
    using ConnectionHandler = std::function<void(asio::ip::tcp::socket)>;

    /**
     * @class PeerNode
     * @brief Core networking infrastructure responsible for establishing and managing TCP connections.
     *        It operates strictly on the network layer and does not handle application-level protocols.
     */
    class PeerNode {
    public:
        PeerNode();
        ~PeerNode();

        /**
         * @brief Starts the ASIO event loop in a dedicated background worker thread.
         */
        void start();

        /**
         * @brief Safely terminates the event loop, closes the acceptor, and joins the worker thread.
         */
        void stop();

        /**
         * @brief Receiver Mode: Opens a port and begins asynchronously accepting incoming connections.
         * @param port The TCP port to listen on.
         * @return True if successfully bound and listening, false otherwise.
         */
        bool startReceiver(uint16_t port);

        /**
         * @brief Sender Mode: Establishes multiple parallel TCP connections to the target endpoint.
         * @param ip The target IPv4 address.
         * @param port The target TCP port.
         * @param session_count Number of parallel TCP pipes to establish (default: 4).
         */
        void startSender(const std::string &ip, uint16_t port, int session_count = 4);

        /**
         * @brief Enqueues a file transfer task to the active sessions.
         * @param file_path The absolute path of the file to send.
         */
        void pushFile(const std::string &file_path);

        // --- Dependency Injection Methods ---
        void set_transfer_callback(TransferCallback callback);
        void set_fd_request_callback(FdRequestCallback callback);
        void set_encryption_key(const std::string &key);

        void set_connection_handler(ConnectionHandler handler) {
            connection_handler_ = std::move(handler);
        }

        std::shared_ptr<SessionManager> get_session_manager() const {
            return session_manager_;
        }

    private:
        /**
         * @brief Asynchronously waits for and processes incoming client connections.
         */
        void do_accept();

        // Optimized socket buffer size for gigabit-level local network transfers (8MB)
        static constexpr size_t TCP_BUFFER_SIZE = 8 * 1024 * 1024;

        asio::io_context io_context_;
        asio::executor_work_guard<asio::io_context::executor_type> work_guard_;
        std::thread worker_thread_;
        asio::ip::tcp::acceptor acceptor_;

        ConnectionHandler connection_handler_;
        TransferCallback transfer_callback_;
        std::shared_ptr<SessionManager> session_manager_;
    };

} // namespace transfer::core