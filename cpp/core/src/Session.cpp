#include "Session.hpp"
#include "SessionManager.hpp"
#include "SenderPipe.hpp"
#include "ReceiverPipe.hpp"
#include "Logger.hpp"
#include "transfer_protocol_generated.h"

// Cross-platform support for htonl (Host to Network Long) byte order conversion.
#ifndef _WIN32
    #include <arpa/inet.h>
#else
    #include <winsock2.h>
#endif

namespace transfer::core {

    // Magic numbers replaced with meaningful constants for readability.
    constexpr size_t SIZE_HEADER_LENGTH = 4;
    constexpr size_t ACK_PAYLOAD_LENGTH = 8;

    // CPU architectures vary (ARM is Little-Endian, some network stacks are Big-Endian).
    // Manual bit-shifting guarantees that the 64-bit integer is consistently
    // packed in Big-Endian order regardless of the host system.
    void Session::serialize_uint64(uint64_t val, uint8_t *buf) {
        for (int i = 0; i < 8; ++i) buf[i] = (val >> (56 - i * 8)) & 0xFF;
    }

    uint64_t Session::deserialize_uint64(const uint8_t *buf) {
        uint64_t val = 0;
        for (int i = 0; i < 8; ++i) val |= (static_cast<uint64_t>(buf[i]) << (56 - i * 8));
        return val;
    }

    Session::Session(std::shared_ptr<asio::ip::tcp::socket> socket, std::shared_ptr<SessionManager> manager)
          : socket_(std::move(socket)),
            manager_(std::move(manager)) {
    }

    Session::~Session() {
        close_socket();
    }

    void Session::close_socket() const {
        if (socket_ && socket_->is_open()) {
            asio::error_code ec;
            // Initiate Graceful Shutdown (TCP 4-Way Handshake).
            // shutdown_both sends a FIN packet, ensuring the peer knows we are done transmitting.
            socket_->shutdown(asio::ip::tcp::socket::shutdown_both, ec);
            socket_->close(ec);
        }
    }

    void Session::start_push_range(const std::string &file_path, uint64_t offset, uint64_t length, uint32_t session_id) {
        LOGI("[Session %d] Delegating push task to SenderPipe (Offset: %llu, Length: %llu)", session_id, offset, length);

        // Instantiate the SenderPipe (Data Plane) and delegate the heavy file I/O to it.
        sender_pipe_ = std::make_shared<SenderPipe>(socket_, manager_, shared_from_this());
        sender_pipe_->start_push_range(file_path, offset, length, session_id);
    }

    void Session::send_handshake(const std::string &name, uint64_t total, uint64_t off, uint64_t len, uint32_t sid, uint32_t chk) {
        // Capture 'self' to extend the Session's lifetime until this async operation completes.
        auto self(shared_from_this());

        flatbuffers::FlatBufferBuilder builder;

        // Construct the Handshake payload natively using Google Flatbuffers.
        auto handshake = transfer::protocol::CreateHandshake(
            builder, builder.CreateString("uuid_placeholder"), builder.CreateString(name), total, off, len, sid, chk);
        builder.Finish(handshake);

        uint32_t payload_size = builder.GetSize();

        // Convert the 32-bit payload size to Network Byte Order (Big-Endian).
        uint32_t net_payload_size = htonl(payload_size);

        // TCP Framing: TCP is a stream protocol, meaning it doesn't preserve message boundaries.
        // We prepend a 4-byte Length Header so the receiver knows exactly how many bytes to read.
        // Frame structure: [4-byte Size Header] + [N-byte Flatbuffers Payload]
        auto buffer = std::make_shared<std::vector<uint8_t>>(SIZE_HEADER_LENGTH + payload_size);
        std::memcpy(buffer->data(), &net_payload_size, SIZE_HEADER_LENGTH);
        std::memcpy(buffer->data() + SIZE_HEADER_LENGTH, builder.GetBufferPointer(), payload_size);

        LOGI("[Session %d] Sending Handshake (Payload Size: %u bytes)", sid, payload_size);

        // Send the Handshake header asynchronously over the network.
        asio::async_write(*socket_, asio::buffer(*buffer),
            [this, self, buffer, sid](std::error_code ec, std::size_t) {
                if (!ec) {
                    // Handshake sent successfully. Now wait for the receiver's green light.
                    receive_handshake_ack();
                } else {
                    LOGE("[Session %d] Handshake send failed: %s", sid, ec.message().c_str());
                    manager_->notify_transfer_error("N/A", "Failed to send Handshake: " + ec.message());
                    close_socket();
                }
            });
    }

    void Session::receive_handshake_ack() {
        auto self(shared_from_this());
        auto ack_buf = std::make_shared<std::vector<uint8_t>>(ACK_PAYLOAD_LENGTH);

        // The receiver replies with an 8-byte progress indicator (saved bytes) for resume capabilities.
        asio::async_read(*socket_, asio::buffer(*ack_buf),
            [this, self, ack_buf](std::error_code ec, std::size_t) {
                if (!ec) {
                    uint64_t saved_progress = deserialize_uint64(ack_buf->data());
                    LOGI("[Session] Handshake ACK received. Peer has saved %llu bytes.", saved_progress);

                    // Control phase completed. Hand over execution to the SenderPipe data plane.
                    if (sender_pipe_) {
                        sender_pipe_->on_handshake_acked(saved_progress);
                    }
                } else {
                    LOGE("[Session] Failed to receive Handshake ACK: %s", ec.message().c_str());
                    manager_->notify_transfer_error("N/A", "Failed to receive Handshake ACK.");
                    close_socket();
                }
            });
    }

    void Session::start_receive_loop() {
        LOGI("[Session] Starting receiver loop. Delegating to ReceiverPipe.");

        // Instantiate the ReceiverPipe (Data Plane) to process the incoming byte stream.
        receiver_pipe_ = std::make_shared<ReceiverPipe>(socket_, manager_, shared_from_this());
        receiver_pipe_->start_receive_loop();
    }

} // namespace transfer::core