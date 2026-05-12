#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <functional>

namespace transfer::core {

    // FdRequestCallback을 다시 정의합니다.
    using FdRequestCallback = std::function<int(const std::string &)>;

    /**
     * @class ReceiverPipe
     * @brief A data plane component responsible for writing chunks to a destination file.
     *        It uses mmap for efficient file access. It has no knowledge of the network layer.
     */
    class ReceiverPipe {
    public:
        /**
         * @brief Constructs a ReceiverPipe. The file is not opened until open() is called.
         * @param save_dir The directory where the received file will be stored.
         * @param fd_callback A callback to request a file descriptor from the host platform.
         */
        ReceiverPipe(const std::string& save_dir, FdRequestCallback fd_callback);
        ~ReceiverPipe();

        /**
         * @brief Creates/opens the destination file and allocates space for it.
         * @param file_name The name of the file to be created.
         * @param total_size The total size of the file to be received.
         * @return True on success, false on failure.
         */
        bool open(const std::string& file_name, uint64_t total_size); // Added file_name

        /**
         * @brief Checks if the file is open and ready for writing.
         * @return True if the pipe is ready, false otherwise.
         */
        bool isOpen() const;

        /**
         * @brief Writes a chunk of data to the file at a specific offset.
         * @param offset The position in the file to write to.
         * @param data The data chunk to write.
         * @return True on success, false on failure.
         */
        bool writeChunk(uint64_t offset, const std::vector<uint8_t>& data);

        /**
         * @brief Closes the file and releases resources.
         */
        void close();

    private:
        std::string save_dir_; // Renamed from save_path_
        FdRequestCallback fd_callback_;

        int fd_ = -1;
        void* mmap_ptr_ = nullptr;
        uint64_t total_size_ = 0;
        std::string full_file_path_; // To store the full path once known
    };

} // namespace transfer::core