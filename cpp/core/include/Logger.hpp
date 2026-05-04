#pragma once

#ifdef __ANDROID__
    #include <android/log.h>
    #define LOG_TAG "TransferCore"
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
    #include <cstdio>
    #define LOGI(...) do { printf("[INFO] " __VA_ARGS__); printf("\n"); } while(0)
    #define LOGE(...) do { fprintf(stderr, "[ERROR] " __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#endif