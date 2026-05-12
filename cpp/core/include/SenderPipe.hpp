#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <functional>

namespace transfer::core {

    using FdRequestCallback = std::function<int(const std::string &)>;

    /**
     * @class SenderPipe
     * @brief A data plane component responsible for reading chunks from a source file.
     *        It uses mmap for efficient file access.
     */
    class SenderPipe {
    public:
        SenderPipe(const std::string& file_path, FdRequestCallback fd_callback);
        ~SenderPipe();

        bool isOpen() const;
        uint64_t getTotalSize() const;

        /**
         * @brief Reads a chunk of data into a vector (legacy method).
         */
        std::vector<uint8_t> readChunk(uint64_t offset, uint32_t size);

        /**
         * @brief Gets the raw memory mapped pointer for Zero-Copy I/O.
         */
        const uint8_t* getMmapPointer() const;

    private:
        void openFile();
        void closeFile();

        std::string file_path_;
        FdRequestCallback fd_callback_;

        int fd_ = -1;
        void* mmap_ptr_ = nullptr;
        uint64_t total_size_ = 0;
    };

} // namespace transfer::core