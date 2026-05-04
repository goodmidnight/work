#pragma once

#include <asio.hpp>
#include <string>
#include <memory>
#include <vector>

namespace transfer::core {

    class SessionManager;
    class Session;

    /**
     * @class SenderPipe
     * @brief Manages the high-speed outbound data stream for a single TCP connection (Data Plane).
     *        It leverages mmap for Zero-Copy reads from disk to the network socket,
     *        handles chunking, and conditionally applies E2E AES encryption.
     */
    class SenderPipe : public std::enable_shared_from_this<SenderPipe> {
    public:
        SenderPipe(std::shared_ptr<asio::ip::tcp::socket> socket,
                   std::shared_ptr<SessionManager> manager,
                   std::shared_ptr<Session> parent_session);
        ~SenderPipe();

        /**
         * @brief Maps the file into memory and triggers the parent Session to send the Handshake.
         * @param offset The starting byte index of the chunk this pipe is responsible for.
         * @param length The total number of bytes this pipe must transmit.
         */
        void start_push_range(const std::string &file_path, uint64_t offset, uint64_t length, uint32_t session_id);

        /**
         * @brief Invoked by the parent Session when the receiver's Handshake ACK arrives.
         * @param resume_offset The number of bytes the receiver already has (enables fast-resume).
         */
        void on_handshake_acked(uint64_t resume_offset);

    private:
        void send_next_chunk();

        // --- Data Transmission Pipeline ---
        void send_chunk(uint32_t chunk_size);

        void cleanup_resources();

        std::shared_ptr<asio::ip::tcp::socket> socket_;
        std::shared_ptr<SessionManager> manager_;
        std::shared_ptr<Session> parent_session_;

        uint32_t session_id_ = 0;
        std::string send_file_name_;

        // --- Send-side File I/O variables ---
        int send_fd_ = -1;
        uint8_t *send_mmap_ptr_ = nullptr;
        uint64_t send_total_file_size_ = 0;

        // Limits defining this pipe's specific workload chunk
        uint64_t send_offset_ = 0;
        uint64_t send_file_size_limit_ = 0;

        // Chunk size optimized for network MTU and CPU Cache locality
        const uint32_t CHUNK_SIZE = 1024 * 1024 * 2;

        // Staging buffer used exclusively when encryption prevents zero-copy
        std::vector<uint8_t> crypto_buffer_; 
    };

} // namespace transfer::core