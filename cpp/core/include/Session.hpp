#pragma once

#include <asio.hpp>
#include <memory>
#include <string>

namespace transfer::core {
    class SessionManager;
    class SenderPipe;
    class ReceiverPipe;

    /**
     * @class Session
     * @brief Acts as the "Control Plane" for a single TCP connection.
     *        It owns the socket, manages the initial Flatbuffers handshake protocol,
     *        and delegates the heavy file I/O to the "Data Plane" (SenderPipe/ReceiverPipe).
     *
     * @note Inherits from std::enable_shared_from_this to ensure the Session object
     *       is not destroyed while asynchronous ASIO callbacks are still pending.
     */
    class Session : public std::enable_shared_from_this<Session> {
    public:
        Session(std::shared_ptr<asio::ip::tcp::socket> socket, std::shared_ptr<SessionManager> manager);
        ~Session();

        /**
         * @brief Spoke (Sender) Entry Point:
         *        Initializes the SenderPipe to push a specific byte range of a file.
         */
        void start_push_range(const std::string &file_path, uint64_t offset, uint64_t length, uint32_t session_id);

        /**
         * @brief Hub (Receiver) Entry Point:
         *        Initializes the ReceiverPipe to start listening for incoming stream data.
         */
        void start_receive_loop();

        /**
         * @brief Constructs and sends the protocol Handshake via Flatbuffers.
         *        This is called by the SenderPipe right before initiating data transfer.
         */
        void send_handshake(const std::string &file_name, uint64_t total_size, uint64_t offset, uint64_t length,
                            uint32_t session_id, uint32_t checksum);

        /**
         * @brief Safely shuts down and closes the TCP socket, sending a FIN packet.
         */
        void close_socket() const;

        // --- Cross-Platform Utility Functions ---
        // Converts 64-bit integers to and from network byte order (Big-Endian).
        static void serialize_uint64(uint64_t val, uint8_t *buf);

        static uint64_t deserialize_uint64(const uint8_t *buf);

    private:
        /**
         * @brief Waits for the receiver to acknowledge the Handshake.
         *        The receiver will reply with an 8-byte progress value for resume capabilities.
         */
        void receive_handshake_ack();

        // Shared ownership of the socket ensures it stays alive as long as any Pipe or Session needs it.
        std::shared_ptr<asio::ip::tcp::socket> socket_;

        // Reference to the global orchestrator.
        std::shared_ptr<SessionManager> manager_;

        // Dedicated Data Plane handlers. Only one will be instantiated per Session.
        std::shared_ptr<SenderPipe> sender_pipe_;
        std::shared_ptr<ReceiverPipe> receiver_pipe_;
    };
} // namespace transfer::core
