#pragma once

#include <asio.hpp>
#include <memory>
#include <string>
#include <vector>
#include <filesystem>

namespace transfer::core {
    class SessionManager;

    class Session : public std::enable_shared_from_this<Session> {
    public:
        Session(asio::ip::tcp::socket socket, std::shared_ptr<SessionManager> manager);
        ~Session();

        void start_receive_loop();

        // 암호화 키 파라미터 제거
        void start_push_range(const std::string &file_path, uint64_t offset, uint64_t length, uint32_t session_id);

    private:
        enum class RecvState { WAIT_HANDSHAKE, RECEIVING_DATA };

        void send_handshake(const std::string &file_name, uint64_t total_size, uint64_t offset, uint64_t length, uint32_t session_id, uint32_t checksum);
        void receive_handshake_ack();
        void send_next_chunk();

        void receive_header();
        void receive_handshake_payload(uint32_t payload_size);
        void receive_raw_data();

        void cleanup_send_resources();
        void cleanup_recv_resources();
        void close_socket();

        static void serialize_uint64(uint64_t val, uint8_t* buf);
        static uint64_t deserialize_uint64(const uint8_t* buf);

        asio::ip::tcp::socket socket_;
        std::shared_ptr<SessionManager> manager_;
        uint32_t session_id_ = 0;

        // --- Send-side variables ---
        int send_fd_ = -1;
        uint8_t *send_mmap_ptr_ = nullptr;
        uint64_t send_total_file_size_ = 0;
        uint64_t send_file_size_limit_ = 0;
        uint64_t send_offset_ = 0;
        const uint32_t CHUNK_SIZE = 1024 * 1024 * 4;

        // --- Receive-side variables ---
        RecvState recv_state_ = RecvState::WAIT_HANDSHAKE;
        int recv_fd_ = -1;
        uint8_t *recv_mmap_ptr_ = nullptr;
        uint64_t recv_total_file_size_ = 0;
        uint64_t recv_offset_ = 0;
        uint64_t recv_limit_ = 0;
        std::string recv_file_name_;
        uint32_t inbound_header_ = 0;
        std::vector<uint8_t> recv_buffer_;

        // 이어받기 메타 파일 변수
        int meta_fd_ = -1;
        uint64_t *meta_mmap_ptr_ = nullptr;
        const size_t META_FILE_SIZE = 64;
    };
} // namespace transfer::core