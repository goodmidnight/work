#include "SenderPipe.hpp"
#include "Logger.hpp"

#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

namespace transfer::core {

SenderPipe::SenderPipe(const std::string& file_path, FdRequestCallback fd_callback)
    : file_path_(file_path), fd_callback_(std::move(fd_callback)) {
    openFile();
}

SenderPipe::~SenderPipe() {
    closeFile();
}

void SenderPipe::openFile() {
    if (fd_callback_) {
        fd_ = fd_callback_(file_path_);
    } else {
        fd_ = ::open(file_path_.c_str(), O_RDONLY);
    }

    if (fd_ < 0) {
        LOGE("[SenderPipe] Failed to open file: %s", file_path_.c_str());
        return;
    }

    struct stat file_stat;
    if (::fstat(fd_, &file_stat) == -1) {
        LOGE("[SenderPipe] Failed to get file stats: %s", file_path_.c_str());
        closeFile();
        return;
    }
    total_size_ = file_stat.st_size;

    if (total_size_ > 0) {
        mmap_ptr_ = ::mmap(nullptr, total_size_, PROT_READ, MAP_SHARED, fd_, 0);
        if (mmap_ptr_ == MAP_FAILED) {
            LOGE("[SenderPipe] Failed to memory map file.");
            mmap_ptr_ = nullptr;
            closeFile();
        }
    }
}

void SenderPipe::closeFile() {
    if (mmap_ptr_) {
        ::munmap(mmap_ptr_, total_size_);
        mmap_ptr_ = nullptr;
    }
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
}

bool SenderPipe::isOpen() const {
    return fd_ >= 0 && (total_size_ == 0 || mmap_ptr_ != nullptr);
}

uint64_t SenderPipe::getTotalSize() const {
    return total_size_;
}

std::vector<uint8_t> SenderPipe::readChunk(uint64_t offset, uint32_t size) {
    if (!isOpen() || offset + size > total_size_) {
        return {};
    }

    const uint8_t* start_ptr = static_cast<const uint8_t*>(mmap_ptr_) + offset;
    return std::vector<uint8_t>(start_ptr, start_ptr + size);
}

const uint8_t* SenderPipe::getMmapPointer() const {
    return static_cast<const uint8_t*>(mmap_ptr_);
}

} // namespace transfer::core