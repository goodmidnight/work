# cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project(TransferProject)

set(CMAKE_CXX_STANDARD 17)

add_subdirectory(core)

if (CMAKE_SYSTEM_NAME STREQUAL "Android")
    add_subdirectory(android)
else()
    add_subdirectory(desktop)
endif()