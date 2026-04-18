#pragma once

#include <asio.hpp>
#include <memory>
#include <string>
#include <thread>
#include "SessionManager.hpp"

#ifdef __ANDROID__
    #include <android/log.h>
    #define LOG_TAG "TransferCore"
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
    #include <cstdio>
    #define LOGI(...) printf("[INFO] " __VA_ARGS__); printf("\n")
    #define LOGE(...) fprintf(stderr, "[ERROR] " __VA_ARGS__); fprintf(stderr, "\n")
#endif

namespace transfer::core {

    class PeerNode {
    public:
        PeerNode();
        ~PeerNode();

        // Starts the ASIO event loop in a background thread.
        void start();

        // Cancels all ongoing asynchronous operations and stops the thread.
        void stop();

        // Server Mode (Hub): Listens for incoming connections on the specified port.
        bool start_listening(uint16_t port);

        // Client Mode (Spoke): Establishes multiple parallel connections (pipes) to the target IP.
        void connect_to_peer(const std::string& ip, uint16_t port, int session_count = 4);

        // Starts a 1:1 file transfer to the connected peer.
        void send_file(const std::string& file_path);

        // Injects a callback function to report transfer state/progress to the upper layer (UI/JNI).
        void set_transfer_callback(TransferCallback callback);

    private:
        // Asynchronously accepts incoming connection requests.
        void do_accept();

        // Core engine for asynchronous I/O operations.
        asio::io_context io_context_;

        // Prevents io_context_.run() from returning immediately when there is no work.
        asio::executor_work_guard<asio::io_context::executor_type> work_guard_;

        // Independent thread to run io_context_.run().
        std::thread worker_thread_;

        // ASIO object responsible for accepting connections.
        asio::ip::tcp::acceptor acceptor_;

        // Manager that handles sessions and coordinates the parallel transfer logic.
        std::shared_ptr<SessionManager> session_manager_;

        // Callback object to store the injected function.
        TransferCallback callback_;
    };

} // namespace transfer::core