#include "SenderPipe.hpp"
#include "SessionManager.hpp"
#include "Session.hpp"
#include "Logger.hpp"

#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <filesystem>

namespace transfer::core {

    SenderPipe::SenderPipe(std::shared_ptr<asio::ip::tcp::socket> socket,
                           std::shared_ptr<SessionManager> manager,
                           std::shared_ptr<Session> parent_session)
        : socket_(std::move(socket)), manager_(std::move(manager)), parent_session_(std::move(parent_session)) {
    }

    SenderPipe::~SenderPipe() {
        cleanup_resources();
    }

    void SenderPipe::start_push_range(const std::string &file_path, uint64_t offset, uint64_t length, uint32_t session_id) {
        session_id_ = session_id;
        std::error_code ec;

        LOGI("[Sender %d] Attempting to map file: %s (Size: %llu)", session_id, file_path.c_str(), length);

        // Extract just the filename for UI progress reporting
        send_file_name_ = std::filesystem::path(file_path).filename().string();
        send_total_file_size_ = std::filesystem::file_size(file_path, ec);

        send_offset_ = offset;
        send_file_size_limit_ = offset + length;

        LOGI("[Sender %d] Total file size: %llu, Offset: %llu, Length: %llu", session_id_, send_total_file_size_, offset, length);

        // Open the physical file in read-only mode
        send_fd_ = ::open(file_path.c_str(), O_RDONLY);
        if (send_fd_ < 0) {
            LOGE("[Sender %d] Failed to open file: %s", session_id_, file_path.c_str());
            manager_->notify_transfer_error(send_file_name_, "Failed to open file for sending.");
            return;
        }

        LOGI("[Sender %d] Calling mmap...", session_id_);

        // --- Memory Mapping (mmap) Setup ---
        // Map the entire file into virtual memory.
        // This allows us to treat a 10GB file exactly like a standard C++ byte array (uint8_t*),
        // letting the OS page the data into RAM automatically as we read it.
        send_mmap_ptr_ = static_cast<uint8_t *>(::mmap(nullptr, send_total_file_size_, PROT_READ, MAP_SHARED, send_fd_, 0));

        if (send_mmap_ptr_ == MAP_FAILED) {
            LOGE("[Sender %d] Failed to memory map file.", session_id_);
            manager_->notify_transfer_error(send_file_name_, "Failed to mmap file.");
            return;
        }
        LOGI("[Sender %d] mmap success. Base Address: %p", session_id_, (void*)send_mmap_ptr_);

        // Pre-allocate staging buffer for the encryption path to avoid reallocation penalties
        if (!manager_->get_encryption_key().empty()) {
            crypto_buffer_.resize(CHUNK_SIZE + 28);
        }

        LOGI("[Sender %d] Triggering Handshake through parent_session.", session_id_);
        // Data preparation is done. Ask the parent Session (Control Plane) to execute the Handshake.
        parent_session_->send_handshake(send_file_name_, send_total_file_size_, offset, length, session_id, 0);
    }

    void SenderPipe::on_handshake_acked(uint64_t resume_offset) {
        LOGI("[Sender %d] Handshake ACK received. Resume offset: %llu", session_id_, resume_offset);
        // The receiver told us they already have some bytes.
        // We simply jump our pointer forward to avoid re-transmitting existing data!
        if (resume_offset > 0) {
            send_offset_ += resume_offset;
            LOGI("[Sender %d] Resuming. Skipped %llu bytes.", session_id_, resume_offset);
        }

        // Kick off the data pumping loop
        send_next_chunk();
    }

    void SenderPipe::send_next_chunk() {
        // Exit condition: We have successfully pushed our assigned byte range
        LOGI("[Sender %d] Next chunk: Offset %llu / Limit %llu", session_id_, send_offset_, send_file_size_limit_);
        if (send_offset_ >= send_file_size_limit_) {
            LOGI("[Sender %d] Chunk transfer completed.", session_id_);
            cleanup_resources();
            manager_->notify_transfer_finished(true);
            return;
        }

        uint64_t remaining = send_file_size_limit_ - send_offset_;
        uint32_t chunk = (remaining < CHUNK_SIZE) ? static_cast<uint32_t>(remaining) : CHUNK_SIZE;

        LOGI("[Sender %d] Prepared chunk size: %u bytes", session_id_, chunk);

        if (manager_->get_encryption_key().empty()) {
            send_chunk(chunk);
        } else {
            //TODO: Not Implement
            send_chunk(chunk);
        }
    }

    void SenderPipe::send_chunk(uint32_t chunk_size) {
        LOGI("[Sender %d] Entering Zerocopy write. Pointer: %p", session_id_, (void*)(send_mmap_ptr_ + send_offset_));
        auto self(shared_from_this());

        // We pass the memory-mapped pointer directly to the ASIO write function.
        // The data flows directly from the Disk Cache -> Kernel Network Stack -> NIC (Network Card),
        // completely bypassing expensive copies into the application's user-space RAM!
        asio::async_write(*socket_, asio::buffer(send_mmap_ptr_ + send_offset_, chunk_size),
            [this, self, chunk_size](std::error_code ec, std::size_t length) {
                if (!ec) {
                    LOGI("[Sender %d] Async write success. Length: %zu", session_id_, length);
                    send_offset_ += length;

                    // Push real-time progress updates up to the UI
                    if (auto cb = manager_->get_callback()) {
                        int progress_pct = static_cast<int>((send_offset_ * 100) / send_total_file_size_);
                        cb(send_file_name_, TransferState::PROGRESS, progress_pct, "Sending...");
                    }

                    // Recursively push the next chunk
                    send_next_chunk();
                } else {
                    LOGE("[Sender %d] Async write error: %s", session_id_, ec.message().c_str());
                    if (ec != asio::error::operation_aborted) {
                        manager_->notify_transfer_error(send_file_name_, "Data write fail: " + ec.message());
                    }
                    cleanup_resources();
                    parent_session_->close_socket();
                    manager_->notify_transfer_finished(false);
                }
            });
    }

    void SenderPipe::cleanup_resources() {
        // Unmap the file from virtual memory to free RAM
        if (send_mmap_ptr_ && send_mmap_ptr_ != MAP_FAILED) {
            ::munmap(send_mmap_ptr_, send_total_file_size_);
            send_mmap_ptr_ = nullptr;
        }

        // Close the physical file descriptor
        if (send_fd_ >= 0) {
            ::close(send_fd_);
            send_fd_ = -1;
        }

        // Deallocate the crypto staging buffer
        crypto_buffer_.clear();
        crypto_buffer_.shrink_to_fit();
    }

} // namespace transfer::core