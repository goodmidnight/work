#!/bin/bash

# Check if a file path is provided as an argument.
if [ -z "$1" ]; then
    echo "Usage: $0 <path_to_file> [session_count]"
    exit 1
fi

FILE_TO_SEND="$1"
SESSION_COUNT=${2:-8} # Default to 8 sessions if not provided for optimal speed

# Check if the file exists.
if [ ! -f "$FILE_TO_SEND" ]; then
    echo "Error: File not found at $FILE_TO_SEND"
    exit 1
fi

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
echo "Starting Sender"
echo "Target: 127.0.0.1:8080"
echo "File: $FILE_TO_SEND"
echo "Sessions: $SESSION_COUNT"
echo "========================================"

# Execute the program in 'send' mode with the new arguments.
"$EXEC_PATH" send --file "$FILE_TO_SEND" --ip 127.0.0.1 --port 8080 --sessions "$SESSION_COUNT"