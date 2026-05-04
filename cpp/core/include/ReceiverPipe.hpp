#pragma once

#include <asio.hpp>
#include <string>
#include <memory>
#include <vector>

namespace transfer::core {

    class SessionManager;
    class Session;

    /**
     * @class ReceiverPipe
     * @brief Manages the high-speed inbound data stream for a single TCP connection (Data Plane).
     *        It handles Handshake reception, Memory-Mapped file I/O (mmap),
     *        resume metadata tracking, and conditionally applies E2E decryption.
     */
    class ReceiverPipe : public std::enable_shared_from_this<ReceiverPipe> {
    public:
        ReceiverPipe(std::shared_ptr<asio::ip::tcp::socket> socket,
                     std::shared_ptr<SessionManager> manager,
                     std::shared_ptr<Session> parent_session);
        ~ReceiverPipe();

        /**
         * @brief Initiates the asynchronous read loop, starting with the 4-byte size header.
         */
        void start_receive_loop();

    private:
        // --- Protocol Handshake ---
        void receive_header();
        void receive_handshake_payload(uint32_t payload_size);

        // --- Data Reception Pipeline ---
        void receive_raw_data();
        void receive_chunk_zerocopy(uint32_t chunk_size);
        void receive_chunk_encrypted(uint32_t chunk_size);

        // --- Utility & Cleanup ---
        void send_handshake_ack(uint64_t saved_progress);
        void cleanup_resources();

        // Helper to serialize the 64-bit progress offset for the ACK packet
        static void serialize_uint64(uint64_t val, uint8_t* buf);

        std::shared_ptr<asio::ip::tcp::socket> socket_;
        std::shared_ptr<SessionManager> manager_;

        // Keep a reference to the parent session to trigger graceful socket closure on errors
        std::shared_ptr<Session> parent_session_;

        uint32_t session_id_ = 0;
        uint32_t inbound_header_ = 0;
        std::string recv_file_name_;

        // --- Receive-side File I/O variables ---
        int recv_fd_ = -1;
        uint8_t *recv_mmap_ptr_ = nullptr;
        uint64_t recv_total_file_size_ = 0;

        // Tracking the exact byte offsets this pipe is responsible for
        uint64_t recv_offset_ = 0;
        uint64_t recv_limit_ = 0;

        // --- Resume Metadata variables ---
        // A tiny parallel file (.meta) used to persistently track how many bytes each session has downloaded.
        // If the app crashes, we read this file to resume exactly where we left off.
        int meta_fd_ = -1;
        uint64_t *meta_mmap_ptr_ = nullptr;
        const size_t META_FILE_SIZE = 64; // Supports up to 8 parallel sessions (8 bytes per uint64_t)

        // Optimized chunk size (2MB) balances memory consumption and network MTU efficiency
        const uint32_t CHUNK_SIZE = 1024 * 1024 * 2;

        // Memory-safe buffers for asynchronous I/O
        std::vector<uint8_t> recv_buffer_;   // For dynamic Flatbuffers payload
        std::vector<uint8_t> crypto_buffer_; // Staging area for decrypting incoming AES-GCM data
    };

} // namespace transfer::core