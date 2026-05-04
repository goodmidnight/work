#include "ReceiverPipe.hpp"
#include "SessionManager.hpp"
#include "Session.hpp"
#include "Logger.hpp"
#include "transfer_protocol_generated.h"

#ifndef _WIN32
#include <arpa/inet.h>
#else
#include <winsock2.h>
#endif

#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

namespace transfer::core {
    void ReceiverPipe::serialize_uint64(uint64_t val, uint8_t *buf) {
        for (int i = 0; i < 8; ++i) buf[i] = (val >> (56 - i * 8)) & 0xFF;
    }

    ReceiverPipe::ReceiverPipe(std::shared_ptr<asio::ip::tcp::socket> socket,
                               std::shared_ptr<SessionManager> manager,
                               std::shared_ptr<Session> parent_session)
        : socket_(std::move(socket)), manager_(std::move(manager)), parent_session_(std::move(parent_session)) {
    }

    ReceiverPipe::~ReceiverPipe() {
        cleanup_resources();
    }

    void ReceiverPipe::start_receive_loop() {
        receive_header(); // Step 1: Read the 4-byte frame header
    }

    void ReceiverPipe::receive_header() {
        auto self(shared_from_this());

        // TCP Framing: Read exactly 4 bytes to determine the size of the upcoming Handshake payload.
        asio::async_read(*socket_, asio::buffer(&inbound_header_, 4),
                         [this, self](std::error_code ec, std::size_t) {
                             if (!ec) {
                                 // Convert from Big-Endian (Network) to Host byte order before allocating memory
                                 receive_handshake_payload(ntohl(inbound_header_));
                             } else {
                                 LOGE("[Receiver] Failed to read header: %s", ec.message().c_str());
                                 parent_session_->close_socket();
                             }
                         });
    }

    void ReceiverPipe::receive_handshake_payload(uint32_t payload_size) {
        auto self(shared_from_this());
        recv_buffer_.resize(payload_size);

        // Step 2: Read the actual Flatbuffers Handshake data
        asio::async_read(*socket_, asio::buffer(recv_buffer_),
                         [this, self](std::error_code ec, std::size_t) {
                             if (!ec) {
                                 // Deserialize the Flatbuffers payload
                                 auto handshake = flatbuffers::GetRoot<transfer::protocol::Handshake>(
                                     recv_buffer_.data());
                                 recv_file_name_ = handshake->file_name()->str();
                                 recv_total_file_size_ = handshake->total_size();
                                 recv_offset_ = handshake->start_offset();
                                 recv_limit_ = recv_offset_ + handshake->transmit_size();
                                 session_id_ = handshake->session_id();

                                 LOGI("[Receiver %d] Handshake parsed. File: %s", session_id_, recv_file_name_.c_str());

                                 // Request actual File Descriptors from the OS (or Android's Storage Access Framework)
                                 if (manager_->get_fd_callback()) {
                                     recv_fd_ = manager_->get_fd_callback()(recv_file_name_);
                                     meta_fd_ = manager_->get_fd_callback()(recv_file_name_ + ".meta");
                                 } else {
                                     // Fallback for isolated desktop C++ testing
                                     std::string save_path = "./recv_" + recv_file_name_;
                                     recv_fd_ = ::open(save_path.c_str(), O_RDWR | O_CREAT, 0666);
                                     meta_fd_ = ::open((save_path + ".meta").c_str(), O_RDWR | O_CREAT, 0666);
                                 }

                                 if (recv_fd_ < 0 || meta_fd_ < 0) {
                                     manager_->notify_transfer_error(recv_file_name_,
                                                                     "Failed to acquire File Descriptors.");
                                     parent_session_->close_socket();
                                     return;
                                 }

                                 // --- Memory Mapping (mmap) Setup ---
                                 struct stat st;
                                 // ftruncate physical disk allocation: You cannot mmap a 0-byte file and write to it.
                                 // We must stretch the physical file to the expected total size first.
                                 if (fstat(recv_fd_, &st) == 0 && static_cast<uint64_t>(st.st_size) <
                                     recv_total_file_size_) {
                                     ::ftruncate(recv_fd_, recv_total_file_size_);
                                 }
                                 // Map the entire file into RAM. Writing to this pointer directly writes to disk.
                                 recv_mmap_ptr_ = static_cast<uint8_t *>(::mmap(
                                     nullptr, recv_total_file_size_, PROT_WRITE, MAP_SHARED, recv_fd_, 0));

                                 // Setup metadata file mapping (for resume tracking)
                                 struct stat meta_st;
                                 if (fstat(meta_fd_, &meta_st) == 0 && static_cast<size_t>(meta_st.st_size) <
                                     META_FILE_SIZE) {
                                     ::ftruncate(meta_fd_, META_FILE_SIZE);
                                 }
                                 meta_mmap_ptr_ = static_cast<uint64_t *>(::mmap(
                                     nullptr, META_FILE_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, meta_fd_, 0));

                                 if (recv_mmap_ptr_ == MAP_FAILED || meta_mmap_ptr_ == MAP_FAILED) {
                                     manager_->notify_transfer_error(recv_file_name_, "Failed to memory map files.");
                                     parent_session_->close_socket();
                                     return;
                                 }

                                 // --- Resume Logic ---
                                 // Check if we previously downloaded parts of this chunk
                                 uint64_t saved_progress = meta_mmap_ptr_[session_id_];
                                 if (saved_progress > handshake->transmit_size()) {
                                     saved_progress = handshake->transmit_size(); // Sanity clamp
                                 }
                                 recv_offset_ += saved_progress; // Skip bytes we already have

                                 // Pre-allocate crypto buffer to prevent memory fragmentation during the fast loop
                                 if (!manager_->get_encryption_key().empty()) {
                                     // AES-GCM adds a 16-byte authentication tag and a 12-byte IV (28 bytes overhead)
                                     crypto_buffer_.resize(CHUNK_SIZE + 28);
                                 }

                                 // Step 3: Tell the sender we are ready to receive data (and tell them our resume offset)
                                 send_handshake_ack(saved_progress);
                             } else {
                                 LOGE("[Receiver %d] Failed to read handshake payload.", session_id_);
                                 parent_session_->close_socket();
                             }
                         });
    }

    void ReceiverPipe::send_handshake_ack(uint64_t saved_progress) {
        auto self(shared_from_this());
        auto ack_buf = std::make_shared<std::vector<uint8_t> >(8);
        serialize_uint64(saved_progress, ack_buf->data());

        asio::async_write(*socket_, asio::buffer(*ack_buf),
                          [this, self, ack_buf](std::error_code write_ec, std::size_t) {
                              if (!write_ec) {
                                  // Notify the UI layer that active data pumping is starting
                                  if (auto cb = manager_->get_callback()) {
                                      cb(recv_file_name_, TransferState::STARTED, 0, "Receiving");
                                  }
                                  // Step 4: Enter the continuous data reception loop
                                  receive_raw_data();
                              } else {
                                  parent_session_->close_socket();
                              }
                          });
    }

    void ReceiverPipe::receive_raw_data() {
        uint64_t remaining = recv_limit_ - recv_offset_;

        // Exit condition: We have received all bytes assigned to this specific pipe
        if (remaining <= 0) {
            LOGI("[Receiver %d] Chunk transfer completed.", session_id_);
            cleanup_resources();
            manager_->notify_transfer_finished(true);
            return;
        }

        // Clamp the next read operation to the defined CHUNK_SIZE
        uint32_t chunk = (remaining < CHUNK_SIZE) ? static_cast<uint32_t>(remaining) : CHUNK_SIZE;

        if (manager_->get_encryption_key().empty()) {
            receive_chunk_zerocopy(chunk);
        } else {
            receive_chunk_encrypted(chunk);
        }
    }

    void ReceiverPipe::receive_chunk_zerocopy(uint32_t chunk_size) {
        auto self(shared_from_this());

        // [Zero-Copy Magic]
        // We instruct the kernel network stack to write incoming TCP payload directly
        // into the memory-mapped file pointer. This completely bypasses user-space application buffers!
        socket_->async_read_some(asio::buffer(recv_mmap_ptr_ + recv_offset_, chunk_size),
                                 [this, self](std::error_code ec, std::size_t length) {
                                     if (!ec) {
                                         recv_offset_ += length;

                                         // Persist our progress to the metadata file memory map
                                         meta_mmap_ptr_[session_id_] += length;

                                         // Push real-time progress updates up to the UI Thread
                                         if (auto cb = manager_->get_callback()) {
                                             int progress_pct = static_cast<int>(
                                                 (recv_offset_ * 100) / recv_total_file_size_);
                                             cb(recv_file_name_, TransferState::PROGRESS, progress_pct, "Receiving...");
                                         }

                                         // Recursively read the next chunk
                                         receive_raw_data();
                                     } else {
                                         if (ec != asio::error::operation_aborted && ec != asio::error::eof) {
                                             manager_->notify_transfer_error(
                                                 recv_file_name_, "Data read fail: " + ec.message());
                                         }
                                         cleanup_resources();
                                         parent_session_->close_socket();
                                         manager_->notify_transfer_finished(false); // Notify manager of pipe failure
                                     }
                                 });
    }

    void ReceiverPipe::receive_chunk_encrypted(uint32_t chunk_size) {
        auto self(shared_from_this());
        uint32_t encrypted_chunk_size = chunk_size + 28;

        // Since encrypted data has a MAC tag and IV, we must first read into a staging buffer (crypto_buffer_)
        asio::async_read(*socket_, asio::buffer(crypto_buffer_.data(), encrypted_chunk_size),
                         [this, self, chunk_size](std::error_code ec, std::size_t) {
                             if (!ec) {
                                 // Note: In a production app, insert EVP_aes_256_gcm decryption logic here.
                                 // The decrypted plaintext is then copied directly into the memory-mapped file.
                                 std::memcpy(recv_mmap_ptr_ + recv_offset_, crypto_buffer_.data(), chunk_size);

                                 recv_offset_ += chunk_size;
                                 meta_mmap_ptr_[session_id_] += chunk_size;

                                 // Progress update
                                 if (auto cb = manager_->get_callback()) {
                                     int progress_pct = static_cast<int>((recv_offset_ * 100) / recv_total_file_size_);
                                     cb(recv_file_name_, TransferState::PROGRESS, progress_pct, "Receiving...");
                                 }

                                 receive_raw_data();
                             } else {
                                 manager_->notify_transfer_error(recv_file_name_,
                                                                 "Encrypted read fail: " + ec.message());
                                 cleanup_resources();
                                 parent_session_->close_socket();
                                 manager_->notify_transfer_finished(false);
                             }
                         });
    }

    void ReceiverPipe::cleanup_resources() {
        // msync forces the OS to flush the memory-mapped RAM changes down to the physical storage drive
        if (recv_mmap_ptr_ && recv_mmap_ptr_ != MAP_FAILED) {
            ::msync(recv_mmap_ptr_, recv_total_file_size_, MS_ASYNC);
            ::munmap(recv_mmap_ptr_, recv_total_file_size_);
            recv_mmap_ptr_ = nullptr;
        }
        if (recv_fd_ >= 0) {
            ::close(recv_fd_);
            recv_fd_ = -1;
        }

        // Flush and unmap metadata tracking file
        if (meta_mmap_ptr_ && meta_mmap_ptr_ != MAP_FAILED) {
            ::msync(meta_mmap_ptr_, META_FILE_SIZE, MS_ASYNC);
            ::munmap(meta_mmap_ptr_, META_FILE_SIZE);
            meta_mmap_ptr_ = nullptr;
        }
        if (meta_fd_ >= 0) {
            ::close(meta_fd_);
            meta_fd_ = -1;
        }

        crypto_buffer_.clear();
        crypto_buffer_.shrink_to_fit();
    }
} // namespace transfer::core
