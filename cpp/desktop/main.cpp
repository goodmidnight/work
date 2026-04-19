#include <iostream>
#include <string>
#include "PeerNode.hpp"

namespace transfer::core {
    enum class TransferState;
}

using namespace transfer::core;

int main(int argc, char* argv[]) {
    if (argc < 2) {
        std::cerr << "Usage:\n"
                  << "  " << argv[0] << " server <port>\n"
                  << "  " << argv[0] << " client <ip> <port>\n";
        return 1;
    }

    std::string mode = argv[1];
    PeerNode node;

    // Register a callback to output engine states (progress, success, error) to the console.
    node.set_transfer_callback([](const std::string& name, TransferState state, int progress, const std::string& msg) {
        std::cout << "\n[Event] File: " << name
                  << " | State: " << static_cast<int>(state)
                  << " | Progress: " << progress << "%"
                  << " | Msg: " << msg << "\n> ";
        std::cout.flush(); // Flush immediately to ensure the input prompt is displayed correctly
    });

    // Start the engine thread first to ensure the ASIO context is running.
    node.start();

    try {
        if (mode == "server" && argc == 3) {
            uint16_t port = static_cast<uint16_t>(std::stoi(argv[2]));
            node.start_listening(port);
        } else if (mode == "client" && argc == 4) {
            std::string ip = argv[2];
            uint16_t port = static_cast<uint16_t>(std::stoi(argv[3]));

            // Connect to the peer using 4 parallel multi-session pipes.
            node.connect_to_peer(ip, port, 4);
        } else {
            std::cerr << "Invalid arguments.\n";
            node.stop();
            return 1;
        }
    } catch (const std::exception& e) {
        std::cerr << "Argument parsing error: " << e.what() << "\n";
        node.stop();
        return 1;
    }

    std::cout << "Enter file path to send (or type 'exit' to quit):\n> ";
    std::string input;

    // Read user input continuously to trigger file transfers.
    while (std::getline(std::cin, input)) {
        if (input == "exit" || input == "quit") {
            break;
        }
        if (!input.empty()) {
            // Execute the 1:1 file transfer API.
            node.send_file(input);
        }
    }

    // Clean up resources and stop the background thread before exiting.
    node.stop();
    return 0;
}