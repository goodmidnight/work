if (CMAKE_SYSTEM_NAME STREQUAL "Android")

    # Define the JNI wrapper source file
    set(JNI_SRC transfer_wrapper_jni.cpp)

    # Create the shared library (.so) for the Android app
    add_library(transfer_jni SHARED ${JNI_SRC})

    # Only functions explicitly marked with JNIEXPORT will be visible to Java/Kotlin
    set_target_properties(transfer_jni PROPERTIES CXX_VISIBILITY_PRESET hidden)

    # Include the core engine headers
    target_include_directories(transfer_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../core/include)

    # Find the built-in Android logging library
    find_library(log-lib log)

    # Link the core static library and Android system libraries
    target_link_libraries(transfer_jni
            PRIVATE
            transfer_core   # Target created in cpp/core/CMakeLists.txt
            ${log-lib}      # For __android_log_print
    )

endif()