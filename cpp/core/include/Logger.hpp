#pragma once

#include <string>
#include <chrono>
#include <iomanip>

// --- Utility to get basename from file path ---
#ifndef _WIN32
#include <libgen.h> // for basename
#else
#include <filesystem> // C++17 filesystem for Windows
#endif

// __BASE_FILE__ is a non-standard but common macro for just the filename.
// Fallback to __FILE__ if it's not available.
#ifndef __BASE_FILE__
#define __BASE_FILE__ __FILE__
#endif


// --- Main Logging Macros ---
#ifdef __ANDROID__
    #include <android/log.h>
    #define LOG_TAG "TransferCore"

    // Android-specific logging
    #define LOG_PRINT(level, fmt, ...) __android_log_print(level, LOG_TAG, "[%s:%d] %s: " fmt, __BASE_FILE__, __LINE__, __func__, ##__VA_ARGS__)

    #define LOGI(fmt, ...) LOG_PRINT(ANDROID_LOG_INFO, fmt, ##__VA_ARGS__)
    #define LOGW(fmt, ...) LOG_PRINT(ANDROID_LOG_WARN, fmt, ##__VA_ARGS__)
    #define LOGE(fmt, ...) LOG_PRINT(ANDROID_LOG_ERROR, fmt, ##__VA_ARGS__)

    #ifndef NDEBUG
        #define LOGD(fmt, ...) LOG_PRINT(ANDROID_LOG_DEBUG, fmt, ##__VA_ARGS__)
        #define LOGV(fmt, ...) LOG_PRINT(ANDROID_LOG_VERBOSE, fmt, ##__VA_ARGS__)
    #else
        #define LOGD(...) do {} while(0)
        #define LOGV(...) do {} while(0)
    #endif

#else // Non-Android platforms (Desktop)
    #include <iostream>
    #include <sstream>

    // Helper to format time
    inline std::string getCurrentTimestamp() {
        auto now = std::chrono::system_clock::now();
        auto in_time_t = std::chrono::system_clock::to_time_t(now);
        std::stringstream ss;
        ss << std::put_time(std::localtime(&in_time_t), "%Y-%m-%d %X");
        return ss.str();
    }

    // basename might modify its argument, so we need a const_cast workaround for string literals.
    #ifdef _WIN32
        #define SAFE_BASENAME(path) std::filesystem::path(path).filename().string()
    #else
        #define SAFE_BASENAME(path) basename(const_cast<char*>(path))
    #endif

    // Generic desktop logging
    #define LOG_PRINT(level, fmt, ...) \
        do { \
            char buffer[2048]; \
            snprintf(buffer, sizeof(buffer), fmt, ##__VA_ARGS__); \
            std::cerr << "[" << getCurrentTimestamp() << "] [" << level << "] [" \
                      << SAFE_BASENAME(__FILE__) << ":" << __LINE__ << " (" << __func__ << ")] " \
                      << buffer << std::endl; \
        } while(0)

    #define LOGI(fmt, ...) LOG_PRINT("INFO", fmt, ##__VA_ARGS__)
    #define LOGW(fmt, ...) LOG_PRINT("WARN", fmt, ##__VA_ARGS__)
    #define LOGE(fmt, ...) LOG_PRINT("ERROR", fmt, ##__VA_ARGS__)

    #ifndef NDEBUG
        #define LOGD(fmt, ...) LOG_PRINT("DEBUG", fmt, ##__VA_ARGS__)
        #define LOGV(fmt, ...) LOG_PRINT("VERBOSE", fmt, ##__VA_ARGS__)
    #else
        #define LOGD(...) do {} while(0)
        #define LOGV(...) do {} while(0)
    #endif

#endif