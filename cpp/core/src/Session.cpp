#include "Session.hpp"
#include "SenderPipe.hpp"
#include "ReceiverPipe.hpp"
#include "Logger.hpp"
#include "transfer_protocol_generated.h" // FlatBuffers
#include <filesystem>
#include <asio/post.hpp>
#include <asio/read.hpp>
#include <asio/write.hpp>
#include <algorithm>

// Platform-specific includes for byte order conversion
#ifndef _WIN32
#include <arpa/inet.h>
#else
#include <winsock2.h>
#endif

namespace transfer::core {
    // A 4-byte header indicating the size of the following FlatBuffers payload.
    constexpr size_t HEADER_SIZE = 4;
    // The size of data chunks to read/write for pure data streams.
    // Larger chunk size helps utilize the OS zero-copy effectively.
    constexpr uint32_t CHUNK_SIZE = 8 * 1024 * 1024; // 8MB

    Session::Session(asio::io_context &io_context,
                     std::shared_ptr<asio::ip::tcp::socket> control_socket,
                     ConnectFn connect_fn)
        : io_context_(io_context),
          control_socket_(std::move(control_socket)),
          connect_fn_(std::move(connect_fn)),
          read_header_buffer_(HEADER_SIZE) {
        LOGI("Session object created.");
    }

    Session::~Session() {
        LOGI("Session object destroyed.");
        close_all_sockets();
    }

    // --- Public Control Methods ---

    void Session::startSend(const std::string &file_path, const std::string& ip, uint16_t port, int session_count) {
        is_sender_ = true;
        file_path_ = file_path;
        target_ip_ = ip;
        target_port_ = port;
        session_count_ = session_count;

        // Initialize the data plane component for reading the file.
        sender_pipe_ = std::make_unique<SenderPipe>(file_path_, fd_request_callback_);
        if (!sender_pipe_->isOpen()) {
            report_error("Failed to open file for sending: " + file_path);
            return;
        }
        total_file_size_ = sender_pipe_->getTotalSize();

        if (status_callback_) {
            status_callback_(TransferState::STARTED, 0, "Sending started");
        }

        // Begin the transfer protocol by sending the initial handshake on the Control Channel.
        send_handshake();
    }

    void Session::startReceive(const std::string &save_path) {
        is_sender_ = false;
        save_path_ = save_path;

        // Initialize the data plane component for writing the file.
        receiver_pipe_ = std::make_unique<ReceiverPipe>(save_path_, fd_request_callback_);

        if (status_callback_) {
            status_callback_(TransferState::STARTED, 0, "Ready to receive");
        }

        // Start the asynchronous read loop on the Control Channel to wait for incoming Handshake.
        do_read_control_header();
    }

    void Session::stop() {
        LOGI("Stop requested for session.");
        asio::post(io_context_, [self = shared_from_this()]() {
            self->close_all_sockets();
        });
    }

    void Session::addDataSocket(std::shared_ptr<asio::ip::tcp::socket> socket) {
        std::lock_guard<std::mutex> lock(sockets_mutex_);
        data_sockets_.push_back(socket);
        LOGI("Data socket added. Total data sockets: %zu", data_sockets_.size());

        // Immediately start reading the DataChannelHello from this new socket
        do_receive_data_channel_hello(socket);
    }

    // --- Internal Utility Methods ---

    void Session::close_all_sockets() {
        auto close_sock = [](std::shared_ptr<asio::ip::tcp::socket>& s) {
            if (s && s->is_open()) {
                asio::error_code ec;
                s->shutdown(asio::ip::tcp::socket::shutdown_both, ec);
                s->close(ec);
            }
        };

        close_sock(control_socket_);
        std::lock_guard<std::mutex> lock(sockets_mutex_);
        for (auto& sock : data_sockets_) {
            close_sock(sock);
        }
        data_sockets_.clear();
    }

    void Session::report_error(const std::string &message) {
        LOGE("Session Error: %s", message.c_str());
        if (status_callback_) {
            status_callback_(TransferState::ERROR, 0, message);
        }
        close_all_sockets();
    }

    // --- Control Channel Asynchronous I/O ---

    void Session::do_read_control_header() {
        auto self = shared_from_this();
        asio::async_read(*control_socket_, asio::buffer(read_header_buffer_),
                         [this, self](const asio::error_code &ec, size_t) {
                             if (!ec) {
                                 uint32_t payload_size;
                                 std::memcpy(&payload_size, read_header_buffer_.data(), HEADER_SIZE);
                                 payload_size = ntohl(payload_size);

                                 if (payload_size > (CHUNK_SIZE + 1024)) {
                                     report_error("Control Payload size too large: " + std::to_string(payload_size));
                                     return;
                                 }
                                 do_read_control_payload(payload_size);
                             } else {
                                 if (ec != asio::error::eof && ec != asio::error::operation_aborted) {
                                     report_error("Failed to read control header: " + ec.message());
                                 }
                             }
                         });
    }

    void Session::do_read_control_payload(uint32_t payload_size) {
        read_payload_buffer_.resize(payload_size);
        auto self = shared_from_this();
        asio::async_read(*control_socket_, asio::buffer(read_payload_buffer_),
                         [this, self](const asio::error_code &ec, size_t) {
                             if (!ec) {
                                 process_control_message();
                             } else if (ec != asio::error::operation_aborted) {
                                 report_error("Failed to read control payload: " + ec.message());
                             }
                         });
    }

    void Session::process_control_message() {
        auto verifier = flatbuffers::Verifier(read_payload_buffer_.data(), read_payload_buffer_.size());
        if (!transfer::protocol::VerifyMessageBuffer(verifier)) {
            report_error("Invalid FlatBuffers message received on Control Channel.");
            return;
        }

        auto msg = transfer::protocol::GetMessage(read_payload_buffer_.data());

        switch (msg->message_type()) {
            case protocol::MessageType_Handshake: handle_handshake(); break;
            case protocol::MessageType_HandshakeAck: handle_handshake_ack(); break;
            case protocol::MessageType_TransferComplete: handle_transfer_complete(); break;
            case protocol::MessageType_Error: handle_error(); break;
            default: report_error("Unknown/Invalid message type on Control Channel."); break;
        }
    }

    // --- Control Channel Message Handlers ---

    void Session::handle_handshake() {
        auto msg = transfer::protocol::GetMessage(read_payload_buffer_.data());
        auto handshake = msg->payload_as_Handshake();
        if (!handshake) { report_error("Invalid Handshake."); return; }

        file_path_ = handshake->file_name()->str();
        total_file_size_ = handshake->total_size();
        session_count_ = handshake->session_count();

        LOGI("Handshake Rx. File: %s, Size: %llu, Expected Sessions: %d", file_path_.c_str(), total_file_size_, session_count_);

        if (!receiver_pipe_ || !receiver_pipe_->open(file_path_, total_file_size_)) {
            report_error("Failed to open receiver pipe for file " + file_path_);
            return;
        }

        // Acknowledge the handshake
        flatbuffers::FlatBufferBuilder builder;
        auto ack = protocol::CreateHandshakeAck(builder, 0); // Resume offset can be added later
        auto message = protocol::CreateMessage(builder, protocol::MessageType_HandshakeAck,
                                               protocol::MessagePayload_HandshakeAck, ack.Union());
        builder.Finish(message);

        auto buffer = std::make_shared<std::vector<uint8_t>>();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*control_socket_, asio::buffer(*buffer), [this, self, buffer](const asio::error_code &ec, size_t) {
            if (!ec) {
                LOGI("Handshake ACK sent. Waiting for Data Channels or Complete message.");
                do_read_control_header();
            } else {
                report_error("Failed to send ACK: " + ec.message());
            }
        });
    }

    void Session::handle_handshake_ack() {
        LOGI("Handshake ACK Rx. Partitioning file and initiating Data Channels.");
        partition_file_and_connect();

        // Keep listening on control channel for Error or TransferComplete
        do_read_control_header();
    }

    void Session::handle_transfer_complete() {
        LOGI("TransferComplete Rx.");
        if (status_callback_) {
            status_callback_(TransferState::COMPLETED, 100, "Transfer complete.");
        }
        close_all_sockets();
    }

    void Session::handle_error() {
        report_error("Received error from peer via Control Channel.");
    }

    // --- Control Channel Senders ---

    void Session::send_handshake() {
        flatbuffers::FlatBufferBuilder builder;
        auto file_name = std::filesystem::path(file_path_).filename().string();
        auto handshake = protocol::CreateHandshake(builder, builder.CreateString(file_name), total_file_size_, session_count_);
        auto message = protocol::CreateMessage(builder, protocol::MessageType_Handshake,
                                               protocol::MessagePayload_Handshake, handshake.Union());
        builder.Finish(message);

        auto buffer = std::make_shared<std::vector<uint8_t>>();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*control_socket_, asio::buffer(*buffer), [this, self, buffer](const asio::error_code &ec, size_t) {
            if (!ec) {
                LOGI("Handshake sent.");
                do_read_control_header();
            } else {
                report_error("Failed to send handshake: " + ec.message());
            }
        });
    }

    void Session::send_transfer_complete() {
        flatbuffers::FlatBufferBuilder builder;
        auto complete = protocol::CreateTransferComplete(builder, true);
        auto message = protocol::CreateMessage(builder, protocol::MessageType_TransferComplete,
                                               protocol::MessagePayload_TransferComplete, complete.Union());
        builder.Finish(message);

        auto buffer = std::make_shared<std::vector<uint8_t>>();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*control_socket_, asio::buffer(*buffer), [this, self, buffer](const asio::error_code &ec, size_t) {
            if (!ec) {
                LOGI("TransferComplete sent.");
                if (status_callback_) status_callback_(TransferState::COMPLETED, 100, "Transfer complete.");
                close_all_sockets();
            } else {
                report_error("Failed to send TransferComplete: " + ec.message());
            }
        });
    }

    // --- Data Channel Setup and Data Transfer ---

    void Session::partition_file_and_connect() {
        std::lock_guard<std::mutex> lock(partitions_mutex_);
        if (session_count_ <= 0) session_count_ = 1;

        uint64_t block_size = total_file_size_ / session_count_;
        active_data_channels_ = session_count_;

        for (int i = 0; i < session_count_; ++i) {
            FilePartition p;
            p.session_id = i;
            p.offset = i * block_size;
            p.length = (i == session_count_ - 1) ? (total_file_size_ - p.offset) : block_size;
            p.bytes_transferred = 0;

            partitions_[i] = p;

            if (connect_fn_) {
                LOGI("Connecting Data Channel %d...", i);
                connect_fn_(target_ip_, target_port_, [this, p](std::shared_ptr<asio::ip::tcp::socket> socket, std::error_code ec) {
                    if (ec || !socket) {
                        report_error("Failed to connect Data Channel: " + ec.message());
                        return;
                    }
                    std::lock_guard<std::mutex> sock_lock(sockets_mutex_);
                    data_sockets_.push_back(socket);
                    do_data_channel_handshake(socket, p);
                });
            }
        }
    }

    void Session::do_data_channel_handshake(std::shared_ptr<asio::ip::tcp::socket> socket, const FilePartition& partition) {
        flatbuffers::FlatBufferBuilder builder;
        auto hello = protocol::CreateDataChannelHello(builder, partition.session_id, partition.offset, partition.length);
        auto message = protocol::CreateMessage(builder, protocol::MessageType_DataChannelHello,
                                               protocol::MessagePayload_DataChannelHello, hello.Union());
        builder.Finish(message);

        auto buffer = std::make_shared<std::vector<uint8_t>>();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*socket, asio::buffer(*buffer), [this, self, socket, partition, buffer](const asio::error_code &ec, size_t) {
            if (!ec) {
                LOGI("DataChannelHello sent for partition %d. Starting true Zero-Copy data transfer loop.", partition.session_id);
                start_data_sender(socket, partition);
            } else {
                report_error("Failed to send DataChannelHello: " + ec.message());
            }
        });
    }

    void Session::do_receive_data_channel_hello(std::shared_ptr<asio::ip::tcp::socket> socket) {
        auto header_buf = std::make_shared<std::vector<uint8_t>>(HEADER_SIZE);
        auto self = shared_from_this();

        asio::async_read(*socket, asio::buffer(*header_buf), [this, self, socket, header_buf](const asio::error_code &ec, size_t) {
            if (!ec) {
                uint32_t payload_size;
                std::memcpy(&payload_size, header_buf->data(), HEADER_SIZE);
                payload_size = ntohl(payload_size);

                if (payload_size > 1024) {
                    report_error("DataChannelHello size too large.");
                    return;
                }

                auto payload_buf = std::make_shared<std::vector<uint8_t>>(payload_size);
                asio::async_read(*socket, asio::buffer(*payload_buf), [this, self, socket, payload_buf](const asio::error_code &e, size_t) {
                    if (!e) {
                        auto verifier = flatbuffers::Verifier(payload_buf->data(), payload_buf->size());
                        if (transfer::protocol::VerifyMessageBuffer(verifier)) {
                            auto msg = transfer::protocol::GetMessage(payload_buf->data());
                            if (msg->message_type() == protocol::MessageType_DataChannelHello) {
                                auto hello = msg->payload_as_DataChannelHello();
                                FilePartition p;
                                p.session_id = hello->session_id();
                                p.offset = hello->offset();
                                p.length = hello->length();
                                p.bytes_transferred = 0;

                                {
                                    std::lock_guard<std::mutex> lock(partitions_mutex_);
                                    partitions_[p.session_id] = p;
                                }
                                LOGI("DataChannelHello Rx. Partition %d, offset %llu, len %llu. Starting true Zero-Copy receive loop.", p.session_id, p.offset, p.length);
                                start_data_receiver(socket, p.session_id);
                            }
                        }
                    }
                });
            } else {
                report_error("Failed to read DataChannelHello header.");
            }
        });
    }

    void Session::start_data_sender(std::shared_ptr<asio::ip::tcp::socket> socket, const FilePartition& partition) {
        uint64_t current_offset = partition.offset + partition.bytes_transferred;
        uint64_t remaining = partition.length - partition.bytes_transferred;

        if (remaining == 0) {
            LOGI("Partition %d sending completed.", partition.session_id);
            int active = --active_data_channels_;
            if (active == 0) {
                LOGI("All data channels completed. Sending TransferComplete.");
                send_transfer_complete();
            }
            return;
        }

        uint32_t size_to_write = std::min(static_cast<uint64_t>(CHUNK_SIZE), remaining);

        const uint8_t* mmap_ptr = sender_pipe_->getMmapPointer();
        if (!mmap_ptr) {
            report_error("File memory mapping is not available for zero-copy.");
            return;
        }

        auto self = shared_from_this();

        // TRUE ZERO-COPY in user-space:
        // We pass the memory-mapped pointer directly to ASIO.
        // ASIO will write directly from the kernel page cache (mmap) into the socket kernel buffer.
        // No std::vector allocations, no memory copying in our application layer!
        asio::async_write(*socket, asio::buffer(mmap_ptr + current_offset, size_to_write),
            [this, self, socket, partition, size_to_write](const asio::error_code &ec, size_t) {
            if (!ec) {
                FilePartition updated_partition = partition;
                updated_partition.bytes_transferred += size_to_write;

                {
                    std::lock_guard<std::mutex> lock(partitions_mutex_);
                    partitions_[partition.session_id] = updated_partition;
                }
                update_progress();

                // Recursively send the next chunk
                start_data_sender(socket, updated_partition);
            } else {
                report_error("Data sender socket write failed: " + ec.message());
            }
        });
    }

    void Session::start_data_receiver(std::shared_ptr<asio::ip::tcp::socket> socket, int session_id) {
        FilePartition p;
        {
            std::lock_guard<std::mutex> lock(partitions_mutex_);
            p = partitions_[session_id];
        }

        uint64_t current_offset = p.offset + p.bytes_transferred;
        uint64_t remaining = p.length - p.bytes_transferred;

        if (remaining == 0) {
            LOGI("Partition %d receive completed.", session_id);
            return;
        }

        uint32_t size_to_read = std::min(static_cast<uint64_t>(CHUNK_SIZE), remaining);

        uint8_t* mmap_ptr = receiver_pipe_->getMmapPointer();
        if (!mmap_ptr) {
            report_error("File memory mapping is not available for zero-copy.");
            return;
        }

        auto self = shared_from_this();

        // TRUE ZERO-COPY in user-space:
        // We pass the memory-mapped pointer directly to ASIO.
        // ASIO will read directly from the socket kernel buffer into the kernel page cache (mmap).
        // No std::vector allocations, no memcpy!
        socket->async_read_some(asio::buffer(mmap_ptr + current_offset, size_to_read),
            [this, self, socket, session_id](const asio::error_code& ec, size_t bytes_transferred) {
            if (!ec) {
                FilePartition updated_p;
                {
                    std::lock_guard<std::mutex> lock(partitions_mutex_);
                    updated_p = partitions_[session_id];
                    updated_p.bytes_transferred += bytes_transferred;
                    partitions_[session_id] = updated_p;
                }

                update_progress();

                // Continue reading
                start_data_receiver(socket, session_id);
            } else {
                if (ec != asio::error::eof && ec != asio::error::operation_aborted) {
                    report_error("Data receiver socket read failed: " + ec.message());
                }
            }
        });
    }

    void Session::update_progress() {
        std::lock_guard<std::mutex> lock(partitions_mutex_);
        uint64_t total_transferred = 0;
        for (const auto& kv : partitions_) {
            total_transferred += kv.second.bytes_transferred;
        }

        int progress = (total_file_size_ > 0) ? static_cast<int>(total_transferred * 100 / total_file_size_) : 0;

        // UI 갱신이 너무 자주 호출되지 않도록 조절 로직 추가 가능
        if (status_callback_) {
            status_callback_(TransferState::PROGRESS, progress, "Transferring...");
        }
    }
} // namespace transfer::core