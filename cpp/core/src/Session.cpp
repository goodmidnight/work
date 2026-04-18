#include "Session.hpp"
#include "SessionManager.hpp"
#include "transfer_protocol_generated.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <arpa/inet.h>

#include "PeerNode.hpp"
#include "flatbuffers/flatbuffer_builder.h"

namespace transfer::core {
    void Session::serialize_uint64(uint64_t val, uint8_t *buf) {
        for (int i = 0; i < 8; ++i) buf[i] = (val >> (56 - i * 8)) & 0xFF;
    }

    uint64_t Session::deserialize_uint64(const uint8_t *buf) {
        uint64_t val = 0;
        for (int i = 0; i < 8; ++i) val |= (uint64_t) buf[i] << (56 - i * 8);
        return val;
    }

    Session::Session(asio::ip::tcp::socket socket, std::shared_ptr<SessionManager> manager)
        : socket_(std::move(socket)), manager_(std::move(manager)) {
    }

    Session::~Session() {
        cleanup_send_resources();
        cleanup_recv_resources();
        close_socket();
    }

    void Session::close_socket() {
        if (socket_.is_open()) {
            asio::error_code ec;
            socket_.close(ec);
        }
    }

    void Session::start_push_range(const std::string &file_path, uint64_t offset, uint64_t length,
                                   uint32_t session_id) {
        session_id_ = session_id;
        std::error_code ec;
        std::string file_name = std::filesystem::path(file_path).filename().string();
        send_total_file_size_ = std::filesystem::file_size(file_path, ec);

        send_offset_ = offset;
        send_file_size_limit_ = offset + length;

        send_fd_ = ::open(file_path.c_str(), O_RDONLY);
        if (send_fd_ < 0) {
            manager_->notify_transfer_error(file_name, "File open error.");
            return;
        }

        send_mmap_ptr_ = static_cast<uint8_t *>(::mmap(nullptr, send_total_file_size_, PROT_READ, MAP_SHARED, send_fd_,
                                                       0));
        if (send_mmap_ptr_ == MAP_FAILED) {
            manager_->notify_transfer_error(file_name, "mmap error.");
            return;
        }
        send_handshake(file_name, send_total_file_size_, offset, length, session_id, 0);
    }

    void Session::send_handshake(const std::string &name, uint64_t total, uint64_t off, uint64_t len, uint32_t sid,
                                 uint32_t chk) {
        auto self(shared_from_this());
        flatbuffers::FlatBufferBuilder builder;

        auto handshake = transfer::protocol::CreateHandshake(
            builder, builder.CreateString("uuid"), builder.CreateString(name), total, off, len, sid, chk);
        builder.Finish(handshake);

        uint32_t payload_size = builder.GetSize();
        uint32_t net_payload_size = htonl(payload_size);
        auto buffer = std::make_shared<std::vector<uint8_t> >(4 + payload_size);
        std::memcpy(buffer->data(), &net_payload_size, 4);
        std::memcpy(buffer->data() + 4, builder.GetBufferPointer(), payload_size);

        asio::async_write(socket_, asio::buffer(*buffer), [this, self, buffer](std::error_code ec, std::size_t) {
            if (!ec) {
                receive_handshake_ack();
            } else {
                manager_->notify_transfer_error("N/A", "Handshake write fail");
                close_socket();
            }
        });
    }

    void Session::receive_handshake_ack() {
        auto self(shared_from_this());
        auto ack_buf = std::make_shared<std::vector<uint8_t> >(8);

        asio::async_read(socket_, asio::buffer(*ack_buf), [this, self, ack_buf](std::error_code ec, std::size_t) {
            if (!ec) {
                uint64_t saved_progress = deserialize_uint64(ack_buf->data());

                if (saved_progress > 0) {
                    send_offset_ += saved_progress;
                    LOGI("[Spoke] Session %d resuming. Skipped %llu bytes.", session_id_, saved_progress);
                }

                send_next_chunk();
            } else {
                manager_->notify_transfer_error("N/A", "Failed to receive ACK");
                close_socket();
            }
        });
    }

    void Session::send_next_chunk() {
        if (send_offset_ >= send_file_size_limit_) {
            cleanup_send_resources();
            manager_->notify_transfer_finished(true);
            return;
        }

        uint64_t remaining = send_file_size_limit_ - send_offset_;
        uint32_t chunk = (remaining < CHUNK_SIZE) ? (uint32_t) remaining : CHUNK_SIZE;
        auto self(shared_from_this());

        // Zero-copy: mmap 포인터에서 직접 소켓으로 전송
        asio::async_write(socket_, asio::buffer(send_mmap_ptr_ + send_offset_, chunk),
                          [this, self, chunk](std::error_code ec, std::size_t length) {
                              if (!ec) {
                                  send_offset_ += length;
                                  send_next_chunk();
                              } else {
                                  if (ec != asio::error::operation_aborted) {
                                      manager_->notify_transfer_error("N/A", "Data write fail: " + ec.message());
                                  }
                                  cleanup_send_resources();
                                  close_socket();
                                  manager_->notify_transfer_finished(false);
                              }
                          });
    }

    void Session::start_receive_loop() { receive_header(); }

    void Session::receive_header() {
        auto self(shared_from_this());
        asio::async_read(socket_, asio::buffer(&inbound_header_, 4), [this, self](std::error_code ec, std::size_t) {
            if (!ec) receive_handshake_payload(ntohl(inbound_header_));
            else close_socket();
        });
    }

    void Session::receive_handshake_payload(uint32_t payload_size) {
        auto self(shared_from_this());
        recv_buffer_.resize(payload_size);

        asio::async_read(socket_, asio::buffer(recv_buffer_), [this, self](std::error_code ec, std::size_t) {
            if (!ec) {
                auto handshake = flatbuffers::GetRoot<transfer::protocol::Handshake>(recv_buffer_.data());
                recv_file_name_ = handshake->file_name()->str();
                recv_total_file_size_ = handshake->total_size();
                recv_offset_ = handshake->start_offset();
                recv_limit_ = recv_offset_ + handshake->transmit_size();
                session_id_ = handshake->session_id();

                std::string save_path = "./recv_" + recv_file_name_;
                recv_fd_ = ::open(save_path.c_str(), O_RDWR | O_CREAT, 0666);

                struct stat st;
                if (fstat(recv_fd_, &st) == 0 && st.st_size < recv_total_file_size_) {
                    ::ftruncate(recv_fd_, recv_total_file_size_);
                }
                recv_mmap_ptr_ = static_cast<uint8_t *>(::mmap(nullptr, recv_total_file_size_, PROT_WRITE, MAP_SHARED,
                                                               recv_fd_, 0));

                std::string meta_path = save_path + ".meta";
                meta_fd_ = ::open(meta_path.c_str(), O_RDWR | O_CREAT, 0666);
                struct stat meta_st;
                if (fstat(meta_fd_, &meta_st) == 0 && meta_st.st_size < META_FILE_SIZE) {
                    ::ftruncate(meta_fd_, META_FILE_SIZE);
                }
                meta_mmap_ptr_ = static_cast<uint64_t *>(::mmap(nullptr, META_FILE_SIZE, PROT_READ | PROT_WRITE,
                                                                MAP_SHARED, meta_fd_, 0));

                uint64_t saved_progress = meta_mmap_ptr_[session_id_];
                if (saved_progress > handshake->transmit_size()) saved_progress = handshake->transmit_size();

                recv_offset_ += saved_progress;

                auto ack_buf = std::make_shared<std::vector<uint8_t> >(8);
                serialize_uint64(saved_progress, ack_buf->data());

                asio::async_write(socket_, asio::buffer(*ack_buf),
                                  [this, self, ack_buf](std::error_code write_ec, std::size_t) {
                                      if (!write_ec) {
                                          if (auto cb = manager_->get_callback()) cb(
                                              recv_file_name_, TransferState::STARTED, 0, "Receiving");
                                          receive_raw_data();
                                      } else {
                                          close_socket();
                                      }
                                  });
            } else {
                close_socket();
            }
        });
    }

    void Session::receive_raw_data() {
        auto self(shared_from_this());
        uint64_t remaining = recv_limit_ - recv_offset_;

        if (remaining <= 0) {
            if (auto cb = manager_->get_callback()) cb(recv_file_name_, TransferState::PROGRESS, 100, "Pipe done");
            cleanup_recv_resources();
            return;
        }

        uint32_t chunk = (remaining < CHUNK_SIZE) ? (uint32_t) remaining : CHUNK_SIZE;

        // Zero-copy: 소켓에서 mmap 포인터로 직접 수신
        socket_.async_read_some(asio::buffer(recv_mmap_ptr_ + recv_offset_, chunk),
                                [this, self](std::error_code ec, std::size_t length) {
                                    if (!ec) {
                                        recv_offset_ += length;
                                        meta_mmap_ptr_[session_id_] += length;

                                        receive_raw_data();
                                    } else {
                                        if (ec != asio::error::operation_aborted && ec != asio::error::eof) {
                                            manager_->notify_transfer_error(
                                                recv_file_name_, "Data read fail: " + ec.message());
                                        }
                                        cleanup_recv_resources();
                                        close_socket();
                                        manager_->notify_transfer_finished(false);
                                    }
                                });
    }

    void Session::cleanup_send_resources() {
        if (send_mmap_ptr_ && send_mmap_ptr_ != MAP_FAILED) {
            ::munmap(send_mmap_ptr_, send_total_file_size_);
            send_mmap_ptr_ = nullptr;
        }
        if (send_fd_ >= 0) {
            ::close(send_fd_);
            send_fd_ = -1;
        }
    }

    void Session::cleanup_recv_resources() {
        if (recv_mmap_ptr_ && recv_mmap_ptr_ != MAP_FAILED) {
            ::msync(recv_mmap_ptr_, recv_total_file_size_, MS_ASYNC);
            ::munmap(recv_mmap_ptr_, recv_total_file_size_);
            recv_mmap_ptr_ = nullptr;
        }
        if (recv_fd_ >= 0) {
            ::close(recv_fd_);
            recv_fd_ = -1;
        }

        if (meta_mmap_ptr_ && meta_mmap_ptr_ != MAP_FAILED) {
            ::msync(meta_mmap_ptr_, META_FILE_SIZE, MS_ASYNC);
            ::munmap(meta_mmap_ptr_, META_FILE_SIZE);
            meta_mmap_ptr_ = nullptr;
        }
        if (meta_fd_ >= 0) {
            ::close(meta_fd_);
            meta_fd_ = -1;
        }
    }
} // namespace transfer::core
