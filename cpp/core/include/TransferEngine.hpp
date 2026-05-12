#pragma once

#include <string>
#include <memory>
#include "SessionManager.hpp" // TransferCallback, FdRequestCallback

namespace transfer::core {

    class PeerNode;

    /**
     * @class TransferEngine
     * @brief The main entry point and facade for the transfer library.
     *        It initializes and coordinates the networking and session management components.
     */
    class TransferEngine {
    public:
        TransferEngine();
        ~TransferEngine();

        // --- Configuration ---
        void set_transfer_callback(const TransferCallback &callback);
        void set_fd_request_callback(const FdRequestCallback &callback);
        void set_encryption_key(const std::string& key);

        // --- Control Methods ---
        /**
         * @brief Starts the engine in receiver mode.
         * @param port The port to listen on.
         * @param save_path The directory or path where incoming files will be saved.
         */
        bool startReceiver(uint16_t port, const std::string& save_path);

        /**
         * @brief Starts the engine in sender mode and initiates a file transfer.
         * @param ip The IP address of the receiver.
         * @param port The port of the receiver.
         * @param file_path The path of the file to send.
         */
        void startSender(const std::string& ip, uint16_t port, const std::string& file_path);

        /**
         * @brief Stops all ongoing operations and shuts down the engine.
         */
        void stop();

    private:
        std::unique_ptr<PeerNode> peer_node_;
        std::shared_ptr<SessionManager> session_manager_;

        // State for sender/receiver mode
        std::string file_to_send_;
        std::string save_path_;
    };

} // namespace transfer::core