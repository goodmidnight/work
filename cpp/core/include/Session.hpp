#pragma once

#include <asio/io_context.hpp>
#include <asio/ip/tcp.hpp>
#include <memory>
#include <string>
#include <functional>
#include <vector>
#include <mutex>
#include "Types.hpp" // Include the new types header

namespace transfer::core {

    // Forward-declare pipe classes to avoid including their headers here.
    class SenderPipe;
    class ReceiverPipe;

    // Callback for reporting session status internally to the SessionManager.
    using StatusCallback = std::function<void(TransferState state, int progress, const std::string& message)>;

    /**
     * @class Session
     * @brief The core control tower for a single-pipeline transfer.
     */
    class Session : public std::enable_shared_from_this<Session> {
    public:
        Session(asio::io_context& io_context, std::shared_ptr<asio::ip::tcp::socket> socket);
        ~Session();

        void startSend(const std::string& file_path);
        void startReceive(const std::string& save_path);
        void stop();

        void set_status_callback(StatusCallback cb) { status_callback_ = std::move(cb); }
        void set_fd_request_callback(FdRequestCallback cb) { fd_request_callback_ = std::move(cb); }

    private:
        void do_read_header();
        void do_read_payload(uint32_t payload_size);
        void process_message();

        void handle_handshake();
        void handle_handshake_ack();
        void handle_data_chunk();
        void handle_transfer_complete();
        void handle_error();

        void send_handshake();
        void send_data_chunks();
        void send_transfer_complete();

        void report_error(const std::string& message);
        void close_socket();

        asio::io_context& io_context_;
        std::shared_ptr<asio::ip::tcp::socket> socket_;

        std::vector<uint8_t> read_header_buffer_;
        std::vector<uint8_t> read_payload_buffer_;

        std::mutex write_mutex_;
        std::vector<uint8_t> write_buffer_;
        void do_write(bool continue_writing);

        StatusCallback status_callback_;
        FdRequestCallback fd_request_callback_;

        std::unique_ptr<SenderPipe> sender_pipe_;
        std::unique_ptr<ReceiverPipe> receiver_pipe_;

        std::string file_path_;
        std::string save_path_;
        uint64_t total_file_size_ = 0;
        uint64_t bytes_transferred_ = 0;
    };
} // namespace transfer::core