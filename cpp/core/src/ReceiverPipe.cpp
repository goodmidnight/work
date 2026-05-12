#include "ReceiverPipe.hpp"
#include "Logger.hpp"

#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring> // for memcpy
#include <filesystem> // For std::filesystem::path

namespace transfer::core {

// Constructor now takes save_dir
ReceiverPipe::ReceiverPipe(const std::string& save_dir, FdRequestCallback fd_callback)
    : save_dir_(save_dir), fd_callback_(std::move(fd_callback)) {
    LOGD("[ReceiverPipe] Constructed with save_dir: %s", save_dir_.c_str());
}

ReceiverPipe::~ReceiverPipe() {
    close();
}

// Open method now takes file_name
bool ReceiverPipe::open(const std::string& file_name, uint64_t total_size) {
    total_size_ = total_size;

    // Construct the full file path
    std::filesystem::path dir_path(save_dir_);
    std::filesystem::path full_path = dir_path / file_name;
    full_file_path_ = full_path.string();

    LOGD("[ReceiverPipe] Attempting to open file. save_dir: '%s', file_name: '%s', full_file_path: '%s'",
         save_dir_.c_str(), file_name.c_str(), full_file_path_.c_str());

    if (fd_callback_) {
        // Delegate file descriptor creation to the platform layer
        fd_ = fd_callback_(full_file_path_);
    } else {
        // For desktop, directly open/create the file
        fd_ = ::open(full_file_path_.c_str(), O_RDWR | O_CREAT, 0666);
    }

    if (fd_ < 0) {
        LOGE("[ReceiverPipe] Failed to open/create file: %s", full_file_path_.c_str());
        return false;
    }

    // Set the file size
    if (::ftruncate(fd_, total_size_) != 0) {
        LOGE("[ReceiverPipe] Failed to truncate file to size %llu for %s", total_size_, full_file_path_.c_str());
        close();
        return false;
    }

    if (total_size_ > 0) {
        mmap_ptr_ = ::mmap(nullptr, total_size_, PROT_WRITE, MAP_SHARED, fd_, 0);
        if (mmap_ptr_ == MAP_FAILED) {
            LOGE("[ReceiverPipe] Failed to memory map file for writing: %s", full_file_path_.c_str());
            mmap_ptr_ = nullptr;
            close();
            return false;
        }
    }
    LOGI("[ReceiverPipe] File opened and mapped for writing: %s (Size: %llu)", full_file_path_.c_str(), total_size_);
    return true;
}

void ReceiverPipe::close() {
    if (mmap_ptr_) {
        // Synchronize changes to disk
        ::msync(mmap_ptr_, total_size_, MS_SYNC);
        ::munmap(mmap_ptr_, total_size_);
        mmap_ptr_ = nullptr;
    }
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    LOGI("[ReceiverPipe] File closed and resources released for %s.", full_file_path_.c_str());
}

bool ReceiverPipe::isOpen() const {
    return fd_ >= 0 && (total_size_ == 0 || mmap_ptr_ != nullptr);
}

bool ReceiverPipe::writeChunk(uint64_t offset, const std::vector<uint8_t>& data) {
    if (!isOpen() || offset + data.size() > total_size_) {
        LOGE("[ReceiverPipe] Write attempt out of bounds for %s. Offset: %llu, Size: %zu, Total: %llu",
             full_file_path_.c_str(), offset, data.size(), total_size_);
        return false;
    }

    uint8_t* dest_ptr = static_cast<uint8_t*>(mmap_ptr_) + offset;
    std::memcpy(dest_ptr, data.data(), data.size());

    return true;
}

} // namespace transfer::core