# Find the flatc compiler
find_program(FLATC_EXECUTABLE flatc)
if (NOT FLATC_EXECUTABLE)
    message(FATAL_ERROR "flatc (FlatBuffers compiler) not found. Please verify the installation.")
endif ()

# Define paths
set(FBS_SCHEMA "${CMAKE_CURRENT_SOURCE_DIR}/proto/transfer_protocol.fbs")
# Generate in the binary (build) directory instead of the source directory to prevent Git tracking
set(GEN_HEADER "${CMAKE_CURRENT_BINARY_DIR}/transfer_protocol_generated.h")

# Define custom command to execute flatc
add_custom_command(
        OUTPUT "${GEN_HEADER}"
        COMMAND ${FLATC_EXECUTABLE} --cpp -o "${CMAKE_CURRENT_BINARY_DIR}" "${FBS_SCHEMA}"
        DEPENDS "${FBS_SCHEMA}"
        COMMENT "Generating FlatBuffers header from ${FBS_SCHEMA}"
        WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
)

# Add the generated header to the source files list (establishes dependency)
set(CORE_SOURCES
        src/PeerNode.cpp
        src/SessionManager.cpp
        src/Session.cpp
        src/TransferEngine.cpp
        src/SenderPipe.cpp
        src/ReceiverPipe.cpp
        "${GEN_HEADER}"
)

# Define the target as a STATIC library
add_library(transfer_core STATIC ${CORE_SOURCES})

# Enforce C++17 standard to guarantee std::filesystem support
target_compile_features(transfer_core PUBLIC cxx_std_17)

# Define macro to use Asio standalone (without Boost)
target_compile_definitions(transfer_core PUBLIC ASIO_STANDALONE)

# Specify header include directories
target_include_directories(transfer_core PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src
        ${CMAKE_CURRENT_SOURCE_DIR}/../libs/asio/include
        ${CMAKE_CURRENT_SOURCE_DIR}/../libs/flatbuffers/include
        ${CMAKE_CURRENT_BINARY_DIR}
)

# Link the thread library on UNIX environments (excluding macOS and Android)
if (UNIX AND NOT APPLE AND NOT ANDROID)
    target_link_libraries(transfer_core PUBLIC pthread)
endif ()