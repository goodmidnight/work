#include <iostream>
#include <string>
#include <vector>
#include <future>
#include "TransferEngine.hpp"
#include "Logger.hpp"

struct Args {
    std::string mode;
    std::string file_path;
    std::string ip;
    uint16_t port = 0;
    std::string save_dir = ".";
    int sessions = 4; // Default to 4 sessions
};

void printUsage(const char* prog_name) {
    std::cerr << "Usage:\n"
              << "  " << prog_name << " send --file <path> --ip <ip> --port <port> [--sessions <count>]\n"
              << "  " << prog_name << " receive --port <port> [--save-dir <path>]\n";
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        printUsage(argv[0]);
        return 1;
    }

    Args args;
    args.mode = argv[1];

    for (int i = 2; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--file" && i + 1 < argc) {
            args.file_path = argv[++i];
        } else if (arg == "--ip" && i + 1 < argc) {
            args.ip = argv[++i];
        } else if (arg == "--port" && i + 1 < argc) {
            args.port = static_cast<uint16_t>(std::stoi(argv[++i]));
        } else if (arg == "--save-dir" && i + 1 < argc) {
            args.save_dir = argv[++i];
        } else if (arg == "--sessions" && i + 1 < argc) { // Parse sessions argument
            args.sessions = std::stoi(argv[++i]);
            if (args.sessions <= 0) args.sessions = 1;
        }
    }

    transfer::core::TransferEngine engine;
    std::promise<bool> transfer_promise;

    engine.set_transfer_callback(
        [&](const std::string &name, transfer::core::TransferState state, int progress, const std::string &msg) {
            if (state == transfer::core::TransferState::PROGRESS) {
                std::cout << "\r[Progress] " << progress << "%" << std::flush;
            } else {
                std::cout << "\n[Event] File: " << name
                          << " | State: " << static_cast<int>(state)
                          << " | Msg: " << msg << std::endl;
            }

            if (state == transfer::core::TransferState::COMPLETED) {
                transfer_promise.set_value(true);
            } else if (state == transfer::core::TransferState::ERROR) {
                transfer_promise.set_value(false);
            }
        });

    if (args.mode == "send") {
        if (args.file_path.empty() || args.ip.empty() || args.port == 0) {
            printUsage(argv[0]);
            return 1;
        }
        LOGI("Starting sender with %d sessions...", args.sessions);
        engine.startSender(args.ip, args.port, args.file_path, args.sessions);

    } else if (args.mode == "receive") {
        if (args.port == 0) {
            printUsage(argv[0]);
            return 1;
        }
        LOGI("Starting receiver...");
        if (!engine.startReceiver(args.port, args.save_dir)) {
            LOGE("Failed to start receiver on port %d", args.port);
            return 1;
        }
        LOGI("Listening on port %d, saving to '%s'...", args.port, args.save_dir.c_str());

    } else {
        printUsage(argv[0]);
        return 1;
    }

    // Wait for the transfer to complete or fail.
    auto transfer_future = transfer_promise.get_future();
    LOGI("Waiting for transfer to complete...");
    bool success = transfer_future.get();

    engine.stop();
    LOGI("Engine stopped.");

    return success ? 0 : 1;
}
