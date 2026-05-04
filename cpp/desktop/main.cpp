#include <iostream>
#include <string>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef _WIN32
#else
#include <unistd.h>
#define OPEN_FUNC open
#define OPEN_FLAGS (O_CREAT | O_RDWR | O_TRUNC)
#define OPEN_PERMS 0644
#endif

#include "TransferEngine.hpp"

namespace transfer::core {
    enum class TransferState;
}

using namespace transfer::core;

int main(int argc, char *argv[]) {
    if (argc < 2) {
        std::cerr << "Usage:\n"
                << "  " << argv[0] << " server <port>\n"
                << "  " << argv[0] << " client <ip> <port>\n";
        return 1;
    }

    const std::string mode = argv[1];
    const TransferEngine engine;

    engine.set_transfer_callback(
        [](const std::string &name, TransferState state, int progress, const std::string &msg) {
            std::cout << "\n[Event] File: " << name
                    << " | State: " << static_cast<int>(state)
                    << " | Progress: " << progress << "%"
                    << " | Msg: " << msg << "\n> ";
            std::cout.flush();
        });

    engine.set_fd_request_callback([](const std::string &filename) -> int {
        int fd = OPEN_FUNC(filename.c_str(), OPEN_FLAGS, OPEN_PERMS);
        if (fd < 0) {
            std::cerr << "\n[Error] Failed to create file descriptor for: " << filename << "\n> ";
        }
        return fd;
    });

    try {
        if (mode == "server" && argc == 3) {
            uint16_t port = static_cast<uint16_t>(std::stoi(argv[2]));
            if (!engine.startReceiver(port)) {
                std::cerr << "Failed to start receiver on port " << port << "\n";
                return 1;
            }
            std::cout << "Listening on port " << port << "...\n";
        } else if (mode == "client" && argc == 4) {
            std::string ip = argv[2];
            uint16_t port = static_cast<uint16_t>(std::stoi(argv[3]));

            engine.startSender(ip, port, 4);
            std::cout << "Connecting to " << ip << ":" << port << "...\n";
        } else {
            std::cerr << "Invalid arguments.\n";
            engine.stop();
            return 1;
        }
    } catch (const std::exception &e) {
        std::cerr << "Argument parsing error: " << e.what() << "\n";
        engine.stop();
        return 1;
    }

    std::cout << "Enter file path to send (or type 'exit' to quit):\n> ";
    std::string input;

    while (std::getline(std::cin, input)) {
        if (input == "exit" || input == "quit") {
            break;
        }
        if (!input.empty()) {
            engine.pushFile(input);
        }
    }

    engine.stop();
    return 0;
}
