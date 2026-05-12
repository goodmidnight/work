#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <functional>

namespace transfer::core {

    // FdRequestCallback을 SessionManager.hpp 대신 여기에 직접 정의하거나,
    // 공통 헤더로 분리할 수 있습니다. 여기서는 직접 정의합니다.
    using FdRequestCallback = std::function<int(const std::string &)>;

    /**
     * @class SenderPipe
     * @brief A data plane component responsible for reading chunks from a source file.
     *        It uses mmap for efficient file access. It has no knowledge of the network layer.
     */
    class SenderPipe {
    public:
        /**
         * @brief Constructs a SenderPipe and opens the specified file.
         * @param file_path The path to the file that will be sent.
         * @param fd_callback A callback to request a file descriptor from the host platform.
         */
        SenderPipe(const std::string& file_path, FdRequestCallback fd_callback);
        ~SenderPipe();

        /**
         * @brief Checks if the file was successfully opened and mapped.
         * @return True if the pipe is ready to read, false otherwise.
         */
        bool isOpen() const;

        /**
         * @brief Gets the total size of the opened file.
         * @return The size of the file in bytes.
         */
        uint64_t getTotalSize() const;

        /**
         * @brief Reads a chunk of data from the file.
         * @param offset The starting position to read from.
         * @param size The number of bytes to read.
         * @return A vector containing the data chunk. Returns an empty vector on failure.
         */
        std::vector<uint8_t> readChunk(uint64_t offset, uint32_t size);

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