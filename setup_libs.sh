#!/bin/bash

echo "Starting ZCP2P external library setup..."

# Create the libs directory
echo "Preparing the cpp/libs directory..."
mkdir -p cpp/libs

# Add Asio and FlatBuffers as Git Submodules
echo "Downloading Asio and FlatBuffers via Git Submodule..."
git submodule add https://github.com/chriskohlhoff/asio.git cpp/libs/asio 2>/dev/null || true
git submodule add https://github.com/google/flatbuffers.git cpp/libs/flatbuffers 2>/dev/null || true

# Install flatc compiler
echo "Checking for flatc compiler installation..."
if ! command -v flatc &> /dev/null; then
    echo "flatc is not installed. Proceeding with installation via Homebrew..."
    if command -v brew &> /dev/null; then
        brew install flatbuffers
    else
        echo "Error: Homebrew is not installed. Cannot automatically install flatc."
        exit 1
    fi
else
    echo "flatc compiler is already installed."
fi

# Compile the transfer_protocol.fbs schema (if the file exists)
echo "Compiling the transfer_protocol.fbs file into a C++ header..."
FBS_FILE="cpp/core/proto/transfer_protocol.fbs"

if [ -f "$FBS_FILE" ]; then
    # Change directory to cpp/core to ensure correct include paths during compilation
    cd cpp/core
    flatc --cpp proto/transfer_protocol.fbs
    cd ../..
    echo "Successfully generated transfer_protocol_generated.h."
else
    echo "Warning: $FBS_FILE not found. Skipping compilation."
fi

echo "Library setup completed successfully."