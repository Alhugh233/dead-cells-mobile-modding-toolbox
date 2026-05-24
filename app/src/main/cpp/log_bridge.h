#pragma once
#include <cstdarg>
#include <cstdio>

#ifdef __cplusplus
extern "C" {
#endif

void log_bridge(const char* tag, const char* msg);

#ifdef __cplusplus
}
#endif

// Convenience: use LOGI/LOGE with tag prefix
#define LOG_BRIDGE(tag, fmt, ...) do { \
    char _buf[1024]; \
    snprintf(_buf, sizeof(_buf), fmt, ##__VA_ARGS__); \
    log_bridge(tag, _buf); \
} while(0)

#define LOGI_PAK(fmt, ...) LOG_BRIDGE("PakTool", fmt, ##__VA_ARGS__)
#define LOGE_PAK(fmt, ...) LOG_BRIDGE("PakTool", fmt, ##__VA_ARGS__)
#define LOGI_ATL(fmt, ...) LOG_BRIDGE("AtlasPack", fmt, ##__VA_ARGS__)
#define LOGE_ATL(fmt, ...) LOG_BRIDGE("AtlasPack", fmt, ##__VA_ARGS__)
