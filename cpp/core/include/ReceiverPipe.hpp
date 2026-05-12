#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <functional>

namespace transfer::core {

    using FdRequestCallback = std::function<int(const std::string &)>;

    /**
     * @class ReceiverPipe
     * @brief A data plane component responsible for writing chunks to a destination file.
     *        It uses mmap for efficient file access.
     */
    class ReceiverPipe {
    public:
        ReceiverPipe(const std::string& save_dir, FdRequestCallback fd_callback);
        ~ReceiverPipe();

        bool open(const std::string& file_name, uint64_t total_size);
        void close();
        bool isOpen() const;

        /**
         * @brief Writes a chunk of data (legacy method).
         */
        bool writeChunk(uint64_t offset, const std::vector<uint8_t>& data);

        /**
         * @brief Gets the raw memory mapped pointer for Zero-Copy I/O.
         */
        uint8_t* getMmapPointer() const;

    private:
        std::string save_dir_;
        std::string full_file_path_;
        FdRequestCallback fd_callback_;

        int fd_ = -1;
        void* mmap_ptr_ = nullptr;
        uint64_t total_size_ = 0;
    };

} // namespace transfer::core