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

# Default port and save directory
PORT=8080
SAVE_DIR="." # Default to current directory

# Parse command line arguments for port and save directory
while [[ "$#" -gt 0 ]]; do
    case "$1" in
        --port)
            PORT="$2"
            shift
            ;;
        --save-dir)
            SAVE_DIR="$2"
            shift
            ;;
        *)
            echo "Unknown parameter passed: $1"
            exit 1
            ;;
    esac
    shift
done

echo "========================================"
echo "Starting Receiver on port: $PORT"
echo "Saving received files to: $SAVE_DIR"
echo "========================================"

# Execute the program in 'receive' mode with the new argument style.
"$EXEC_PATH" receive --port "$PORT" --save-dir "$SAVE_DIR"