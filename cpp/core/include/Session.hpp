#pragma once

#include <asio/io_context.hpp>
#include <asio/ip/tcp.hpp>
#include <memory>
#include <string>
#include <functional>
#include <vector>
#include <mutex>
#include <map>
#include <system_error>
#include "Types.hpp"

namespace transfer::core {

    class SenderPipe;
    class ReceiverPipe;

    using StatusCallback = std::function<void(TransferState state, int progress, const std::string& message)>;
    using ConnectionHandler = std::function<void(std::shared_ptr<asio::ip::tcp::socket>, std::error_code)>;
    using ConnectFn = std::function<void(const std::string&, uint16_t, ConnectionHandler)>;

    struct FilePartition {
        int session_id;
        uint64_t offset;
        uint64_t length;
        uint64_t bytes_transferred;
    };

    class Session : public std::enable_shared_from_this<Session> {
    public:
        Session(asio::io_context& io_context,
                std::shared_ptr<asio::ip::tcp::socket> control_socket,
                ConnectFn connect_fn = nullptr);
        ~Session();

        void startSend(const std::string& file_path, const std::string& ip, uint16_t port, int session_count);
        void startReceive(const std::string& save_path);
        void stop();

        // Called by SessionManager on the receiver side when a new socket connects
        void addDataSocket(std::shared_ptr<asio::ip::tcp::socket> socket);

        void set_status_callback(StatusCallback cb) { status_callback_ = std::move(cb); }
        void set_fd_request_callback(FdRequestCallback cb) { fd_request_callback_ = std::move(cb); }

    private:
        // Control Channel I/O
        void do_read_control_header();
        void do_read_control_payload(uint32_t payload_size);
        void process_control_message();

        void handle_handshake();
        void handle_handshake_ack();
        void handle_transfer_complete();
        void handle_error();

        void send_handshake();
        void send_transfer_complete();

        void report_error(const std::string& message);
        void close_all_sockets();

        // Data Channel I/O
        void partition_file_and_connect();
        void do_data_channel_handshake(std::shared_ptr<asio::ip::tcp::socket> socket, const FilePartition& partition);
        void do_receive_data_channel_hello(std::shared_ptr<asio::ip::tcp::socket> socket);

        void start_data_sender(std::shared_ptr<asio::ip::tcp::socket> socket, const FilePartition& partition);
        void start_data_receiver(std::shared_ptr<asio::ip::tcp::socket> socket, int session_id);

        void update_progress();

        asio::io_context& io_context_;
        std::shared_ptr<asio::ip::tcp::socket> control_socket_;
        ConnectFn connect_fn_;

        std::mutex sockets_mutex_;
        std::vector<std::shared_ptr<asio::ip::tcp::socket>> data_sockets_;

        std::vector<uint8_t> read_header_buffer_;
        std::vector<uint8_t> read_payload_buffer_;

        StatusCallback status_callback_;
        FdRequestCallback fd_request_callback_;

        std::unique_ptr<SenderPipe> sender_pipe_;
        std::unique_ptr<ReceiverPipe> receiver_pipe_;

        std::string file_path_;
        std::string save_path_;
        std::string target_ip_;
        uint16_t target_port_ = 0;
        int session_count_ = 1;
        uint64_t total_file_size_ = 0;

        std::mutex partitions_mutex_;
        std::map<int, FilePartition> partitions_;

        bool is_sender_ = false;
        int active_data_channels_ = 0;
    };
} // namespace transfer::core