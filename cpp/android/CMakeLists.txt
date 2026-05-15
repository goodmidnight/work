cmake_minimum_required(VERSION 3.22.1)
project(transfer_android_jni)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../core ${CMAKE_CURRENT_BINARY_DIR}/core_build)

if (CMAKE_SYSTEM_NAME STREQUAL "Android")

    #  Define the JNI wrapper source file
    set(JNI_SRC TransferWrapperJni.cpp)

    # Create the shared library (.so) for the Android app
    add_library(transfer_jni SHARED ${JNI_SRC})

    # Optimize binary size and hide internal symbols
    set_target_properties(transfer_jni PROPERTIES CXX_VISIBILITY_PRESET hidden)

    # Include the core engine headers (e.g., PeerNode.hpp)
    target_include_directories(transfer_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../core/include)

    #  Find the built-in Android logging library
    find_library(log-lib log)

    #  Link the core static library and Android system libraries
    target_link_libraries(transfer_jni
            PRIVATE
            transfer_core
            ${log-lib}      # For __android_log_print
    )

endif()