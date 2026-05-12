#include "Session.hpp"
#include "SenderPipe.hpp"
#include "ReceiverPipe.hpp"
#include "Logger.hpp"
#include "transfer_protocol_generated.h" // FlatBuffers
#include <filesystem>
#include <asio/post.hpp>
#include <asio/read.hpp>
#include <asio/write.hpp>
#include <algorithm> // for std::min

// Platform-specific includes for byte order conversion
#ifndef _WIN32
#include <arpa/inet.h>
#else
#include <winsock2.h>
#endif

namespace transfer::core {
    // A 4-byte header indicating the size of the following FlatBuffers payload.
    constexpr size_t HEADER_SIZE = 4;
    // The size of data chunks to read from the file and send. 2MB is a good balance.
    constexpr uint32_t CHUNK_SIZE = 2 * 1024 * 1024;

    Session::Session(asio::io_context &io_context, std::shared_ptr<asio::ip::tcp::socket> socket)
        : io_context_(io_context),
          socket_(std::move(socket)),
          read_header_buffer_(HEADER_SIZE) {
        LOGI("Session object created.");
    }

    Session::~Session() {
        LOGI("Session object destroyed.");
        close_socket();
    }

    // --- Public Control Methods ---

    void Session::startSend(const std::string &file_path) {
        file_path_ = file_path;

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

        // Begin the transfer protocol by sending the initial handshake.
        send_handshake();
    }

    void Session::startReceive(const std::string &save_path) {
        save_path_ = save_path;
        // Initialize the data plane component for writing the file.
        receiver_pipe_ = std::make_unique<ReceiverPipe>(save_path_, fd_request_callback_);

        if (status_callback_) {
            status_callback_(TransferState::STARTED, 0, "Ready to receive");
        }

        // Start the asynchronous read loop to wait for incoming messages.
        do_read_header();
    }

    void Session::stop() {
        LOGI("Stop requested for session.");
        // Post the close operation to the io_context to ensure thread safety.
        asio::post(io_context_, [self = shared_from_this()]() {
            self->close_socket();
        });
    }

    // --- Internal Utility Methods ---

    void Session::close_socket() {
        if (socket_ && socket_->is_open()) {
            asio::error_code ec;
            // Gracefully shut down the connection.
            socket_->shutdown(asio::ip::tcp::socket::shutdown_both, ec);
            socket_->close(ec);
            LOGI("Socket closed.");
        }
    }

    void Session::report_error(const std::string &message) {
        LOGE("Session Error: %s", message.c_str());
        if (status_callback_) {
            // Notify the upper layer (SessionManager) about the error.
            status_callback_(TransferState::ERROR, 0, message);
        }
        // An error is fatal. Close the connection.
        close_socket();
    }

    // --- Asynchronous I/O Read Loop ---

    void Session::do_read_header() {
        // Use shared_from_this to keep the Session object alive during async operations.
        auto self = shared_from_this();
        asio::async_read(*socket_, asio::buffer(read_header_buffer_),
                         [this, self](const asio::error_code &ec, size_t) {
                             if (!ec) {
                                 // The header contains the size of the payload in network byte order.
                                 uint32_t payload_size;
                                 std::memcpy(&payload_size, read_header_buffer_.data(), HEADER_SIZE);
                                 payload_size = ntohl(payload_size);

                                 // Sanity check to prevent allocating huge amounts of memory.
                                 if (payload_size > (CHUNK_SIZE + 1024)) {
                                     report_error("Payload size too large: " + std::to_string(payload_size));
                                     return;
                                 }
                                 // Now that we have the size, read the actual payload.
                                 do_read_payload(payload_size);
                             } else {
                                 if (ec != asio::error::eof && ec != asio::error::operation_aborted) {
                                     report_error("Failed to read header: " + ec.message());
                                 } else {
                                     LOGI("Connection closed by peer (EOF).");
                                 }
                             }
                         });
    }

    void Session::do_read_payload(uint32_t payload_size) {
        read_payload_buffer_.resize(payload_size);
        auto self = shared_from_this();
        asio::async_read(*socket_, asio::buffer(read_payload_buffer_),
                         [this, self](const asio::error_code &ec, size_t) {
                             if (!ec) {
                                 // We have the complete message, now process it.
                                 process_message();
                             } else if (ec != asio::error::operation_aborted) {
                                 report_error("Failed to read payload: " + ec.message());
                             }
                         });
    }

    void Session::process_message() {
        // Verify that the received data is a valid FlatBuffers message.
        auto verifier = flatbuffers::Verifier(read_payload_buffer_.data(), read_payload_buffer_.size());
        if (!transfer::protocol::VerifyMessageBuffer(verifier)) {
            report_error("Invalid FlatBuffers message received.");
            return;
        }

        auto msg = transfer::protocol::GetMessage(read_payload_buffer_.data());

        // Delegate to the appropriate handler based on the message type.
        switch (msg->message_type()) {
            case protocol::MessageType_Handshake: handle_handshake();
                break;
            case protocol::MessageType_HandshakeAck: handle_handshake_ack();
                break;
            case protocol::MessageType_DataChunk: handle_data_chunk();
                break;
            case protocol::MessageType_TransferComplete: handle_transfer_complete();
                break;
            case protocol::MessageType_Error: handle_error();
                break;
            default: report_error("Unknown message type received.");
                break;
        }
    }

    // --- Message Handlers ---

    void Session::handle_handshake() {
        auto msg = transfer::protocol::GetMessage(read_payload_buffer_.data());
        auto handshake = msg->payload_as_Handshake();
        if (!handshake) {
            report_error("Invalid Handshake payload.");
            return;
        }

        // Extract file metadata from the handshake.
        file_path_ = handshake->file_name()->str();
        total_file_size_ = handshake->total_size();
        bytes_transferred_ = 0;

        LOGI("Handshake received for file: %s, size: %llu", file_path_.c_str(), total_file_size_);

        // Prepare the receiver pipe to write the file.
        if (!receiver_pipe_ || !receiver_pipe_->open(file_path_, total_file_size_)) {
            report_error("Failed to open receiver pipe for file " + file_path_);
            return;
        }

        // Acknowledge the handshake, indicating we are ready to receive from offset 0.
        flatbuffers::FlatBufferBuilder builder;
        auto ack = protocol::CreateHandshakeAck(builder, 0);
        auto message = protocol::CreateMessage(builder, protocol::MessageType_HandshakeAck,
                                               protocol::MessagePayload_HandshakeAck, ack.Union());
        builder.Finish(message);

        // This is a simplified write. A more robust implementation would use a write queue.
        auto buffer = std::make_shared<std::vector<uint8_t> >();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*socket_, asio::buffer(*buffer), [this, self, buffer](const asio::error_code &ec, size_t) {
            if (!ec) {
                LOGI("Handshake ACK sent.");
                // Continue the read loop for the next message (e.g., DataChunk).
                do_read_header();
            } else {
                report_error("Failed to send Handshake ACK: " + ec.message());
            }
        });
    }

    void Session::handle_handshake_ack() {
        auto msg = transfer::protocol::GetMessage(read_payload_buffer_.data());
        auto ack = msg->payload_as_HandshakeAck();
        if (!ack) {
            report_error("Invalid HandshakeAck payload.");
            return;
        }

        // The receiver may ask us to resume from a specific offset.
        uint64_t resume_offset = ack->resume_offset();
        LOGI("Handshake ACK received. Resume offset: %llu", resume_offset);
        bytes_transferred_ = resume_offset;

        // The handshake is complete. Start sending the actual file data.
        send_data_chunks();
    }

    void Session::handle_data_chunk() {
        auto msg = transfer::protocol::GetMessage(read_payload_buffer_.data());
        auto data_chunk = msg->payload_as_DataChunk();
        if (!data_chunk) {
            report_error("Invalid DataChunk payload.");
            return;
        }

        uint64_t offset = data_chunk->offset();
        const auto *payload_vec = data_chunk->payload();
        std::vector<uint8_t> data(payload_vec->begin(), payload_vec->end());

        // Write the received data to the file via the receiver pipe.
        if (!receiver_pipe_ || !receiver_pipe_->writeChunk(offset, data)) {
            report_error("Failed to write chunk to file.");
            return;
        }

        // Update progress and notify the upper layer.
        bytes_transferred_ += data.size();
        int progress = (total_file_size_ > 0) ? static_cast<int>(bytes_transferred_ * 100 / total_file_size_) : 0;
        if (status_callback_) {
            status_callback_(TransferState::PROGRESS, progress, "Receiving...");
        }

        // Continue reading for the next chunk.
        do_read_header();
    }

    void Session::handle_transfer_complete() {
        LOGI("TransferComplete received.");
        if (status_callback_) {
            status_callback_(TransferState::COMPLETED, 100, "Transfer complete.");
        }
        // The transfer is finished, so we can close the connection.
        close_socket();
    }

    void Session::handle_error() {
        auto msg = transfer::protocol::GetMessage(read_payload_buffer_.data());
        auto error_msg = msg->payload_as_Error();
        std::string err_str = error_msg && error_msg->message() ? error_msg->message()->str() : "Unknown error";
        report_error("Received error from peer: " + err_str);
    }

    // --- Asynchronous I/O Write Logic ---

    void Session::send_handshake() {
        flatbuffers::FlatBufferBuilder builder;
        auto file_name = std::filesystem::path(file_path_).filename().string();
        auto handshake = protocol::CreateHandshake(builder, builder.CreateString(file_name), total_file_size_);
        auto message = protocol::CreateMessage(builder, protocol::MessageType_Handshake,
                                               protocol::MessagePayload_Handshake, handshake.Union());
        builder.Finish(message);

        auto buffer = std::make_shared<std::vector<uint8_t> >();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*socket_, asio::buffer(*buffer), [this, self, buffer](const asio::error_code &ec, size_t) {
            if (!ec) {
                LOGI("Handshake sent. Waiting for ACK.");
                // Start reading for the HandshakeAck response.
                do_read_header();
            } else {
                report_error("Failed to send handshake: " + ec.message());
            }
        });
    }

    void Session::send_data_chunks() {
        // Exit condition for the recursive send loop.
        if (bytes_transferred_ >= total_file_size_) {
            send_transfer_complete();
            return;
        }

        // Determine the size of the next chunk to send.
        uint32_t size_to_read = std::min(CHUNK_SIZE, (uint32_t) (total_file_size_ - bytes_transferred_));
        std::vector<uint8_t> chunk_data = sender_pipe_->readChunk(bytes_transferred_, size_to_read);

        if (chunk_data.empty() && size_to_read > 0) {
            report_error("Failed to read chunk from file.");
            return;
        }

        // Build the DataChunk message.
        flatbuffers::FlatBufferBuilder builder;
        auto payload = builder.CreateVector(chunk_data.data(), chunk_data.size());
        auto data_chunk = protocol::CreateDataChunk(builder, bytes_transferred_, payload);
        auto message = protocol::CreateMessage(builder, protocol::MessageType_DataChunk,
                                               protocol::MessagePayload_DataChunk, data_chunk.Union());
        builder.Finish(message);

        auto buffer = std::make_shared<std::vector<uint8_t> >();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*socket_, asio::buffer(*buffer),
                          [this, self, buffer, size_to_read](const asio::error_code &ec, size_t) {
                              if (!ec) {
                                  // Update progress after the chunk is successfully sent.
                                  bytes_transferred_ += size_to_read;
                                  int progress = (total_file_size_ > 0)
                                                     ? static_cast<int>(bytes_transferred_ * 100 / total_file_size_)
                                                     : 0;
                                  if (status_callback_) {
                                      status_callback_(TransferState::PROGRESS, progress, "Sending...");
                                  }
                                  // Recursively call to send the next chunk.
                                  send_data_chunks();
                              } else {
                                  report_error("Failed to send data chunk: " + ec.message());
                              }
                          });
    }

    void Session::send_transfer_complete() {
        flatbuffers::FlatBufferBuilder builder;
        auto complete = protocol::CreateTransferComplete(builder, true);
        auto message = protocol::CreateMessage(builder, protocol::MessageType_TransferComplete,
                                               protocol::MessagePayload_TransferComplete, complete.Union());
        builder.Finish(message);

        auto buffer = std::make_shared<std::vector<uint8_t> >();
        uint32_t size = builder.GetSize();
        buffer->resize(HEADER_SIZE + size);
        uint32_t net_size = htonl(size);
        std::memcpy(buffer->data(), &net_size, HEADER_SIZE);
        std::memcpy(buffer->data() + HEADER_SIZE, builder.GetBufferPointer(), size);

        auto self = shared_from_this();
        asio::async_write(*socket_, asio::buffer(*buffer), [this, self, buffer](const asio::error_code &ec, size_t) {
            if (!ec) {
                LOGI("TransferComplete sent.");
                if (status_callback_) {
                    status_callback_(TransferState::COMPLETED, 100, "Transfer complete.");
                }
                // The sender can close the connection after sending the final message.
                // The receiver will close upon receiving it.
                close_socket();
            } else {
                report_error("Failed to send TransferComplete: " + ec.message());
            }
        });
    }

    void Session::do_write(bool) {
        // TODO: Implement a robust, thread-safe write queue to prevent concurrent writes.
        // This is a placeholder for a future improvement.
    }
} // namespace transfer::core
