#!/bin/bash

# Resolve the absolute path of the directory containing this script (cpp/desktop)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Point to the executable in the cmake-build-debug directory
EXEC_PATH="$SCRIPT_DIR/../cmake-build-debug/desktop/transfer_desktop"

# Check if the executable exists
if [ ! -f "$EXEC_PATH" ]; then
    echo "Error: Executable not found at $EXEC_PATH"
    echo "Please build the project using CMake first."
    exit 1
fi

echo "========================================"
echo "Starting Hub (Server) on port: 8080"
echo "========================================"

"$EXEC_PATH" server 8080