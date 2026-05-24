#include <jni.h>
#include <android/asset_manager.h>
#include <dlfcn.h>
#include <dirent.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>
#include <cerrno>
#include <climits>
#include <string>
#include <map>
#include <mutex>
#include <vector>
#include <utility>
#include <algorithm>

#include <android/log.h>

#define TAG  "AssetReplacer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─── LSPosed native API types ───────────────────────────────────────

typedef int  (*HookFunType)(void *func, void *replace, void **backup);
typedef int  (*UnhookFunType)(void *func);
typedef void (*NativeOnModuleLoaded)(const char *name, void *handle);

typedef struct {
    uint32_t       version;
    HookFunType    hook_func;
    UnhookFunType  unhook_func;
} NativeAPIEntries;

typedef NativeOnModuleLoaded (*NativeInit)(const NativeAPIEntries *entries);

static HookFunType g_hook = nullptr;

// ─── AAsset function signatures ─────────────────────────────────────

typedef AAsset* (*PFN_AAssetManager_open)(AAssetManager*, const char*, int);
typedef AAssetDir* (*PFN_AAssetManager_openDir)(AAssetManager*, const char*);
typedef void (*PFN_AAssetDir_close)(AAssetDir*);
typedef const char* (*PFN_AAssetDir_getNextFileName)(AAssetDir*);
typedef void (*PFN_AAssetDir_rewind)(AAssetDir*);

typedef int    (*PFN_AAsset_read)(AAsset*, void*, size_t);
typedef off_t  (*PFN_AAsset_seek)(AAsset*, off_t, int);
typedef off64_t(*PFN_AAsset_seek64)(AAsset*, off64_t, int);
typedef void   (*PFN_AAsset_close)(AAsset*);
typedef off_t  (*PFN_AAsset_getLength)(AAsset*);
typedef off64_t(*PFN_AAsset_getLength64)(AAsset*);
typedef off_t  (*PFN_AAsset_getRemainingLength)(AAsset*);
typedef off64_t(*PFN_AAsset_getRemainingLength64)(AAsset*);
typedef int    (*PFN_AAsset_openFileDescriptor)(AAsset*, off_t*, off_t*);
typedef int    (*PFN_AAsset_openFileDescriptor64)(AAsset*, off64_t*, off64_t*);

// ─── Original function pointers ─────────────────────────────────────

static PFN_AAssetManager_open          orig_AAssetManager_open          = nullptr;
static PFN_AAssetManager_openDir       orig_AAssetManager_openDir       = nullptr;
static PFN_AAssetDir_close             orig_AAssetDir_close             = nullptr;
static PFN_AAssetDir_getNextFileName   orig_AAssetDir_getNextFileName   = nullptr;
static PFN_AAssetDir_rewind            orig_AAssetDir_rewind            = nullptr;

static PFN_AAsset_read                 orig_AAsset_read                 = nullptr;
static PFN_AAsset_seek                 orig_AAsset_seek                 = nullptr;
static PFN_AAsset_seek64               orig_AAsset_seek64               = nullptr;
static PFN_AAsset_close                orig_AAsset_close                = nullptr;
static PFN_AAsset_getLength            orig_AAsset_getLength            = nullptr;
static PFN_AAsset_getLength64          orig_AAsset_getLength64          = nullptr;
static PFN_AAsset_getRemainingLength   orig_AAsset_getRemainingLength   = nullptr;
static PFN_AAsset_getRemainingLength64 orig_AAsset_getRemainingLength64 = nullptr;
static PFN_AAsset_openFileDescriptor   orig_AAsset_openFileDescriptor   = nullptr;
static PFN_AAsset_openFileDescriptor64 orig_AAsset_openFileDescriptor64 = nullptr;

// ─── Replacement state ──────────────────────────────────────────────

struct AssetReplacement {
    uint8_t*    data;
    size_t      size;
    off_t       offset;
    std::string external_path;
    bool        is_proxy;   // true if the AAsset* came from a proxy (asset not in APK)
};

static std::mutex                     g_mutex;
static std::map<AAsset*, AssetReplacement> g_replacements;

// asset_name -> external file path (populated by directory scan)
static std::map<std::string, std::string> g_targets;

// cached name of any existing asset, used as proxy for injection
static std::string g_proxy_asset_name;
static AAssetManager* g_last_mgr = nullptr;

// ─── Directory-based file listing injection ─────────────────────────

struct DirState {
    std::vector<std::string> injected;
    size_t injected_idx = 0;
    bool   done_original = false;
};

static std::mutex                  g_dir_mutex;
static std::map<AAssetDir*, DirState> g_dir_states;

// ─── Recursive directory scanner ────────────────────────────────────

static void scan_dir(const std::string& root, const std::string& prefix) {
    DIR* dir = opendir(root.c_str());
    if (!dir) {
        LOGW("Cannot open directory: %s (%s)", root.c_str(), strerror(errno));
        return;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;

        std::string full  = root + "/" + entry->d_name;
        std::string asset = prefix.empty() ? entry->d_name : prefix + "/" + entry->d_name;

        if (entry->d_type == DT_DIR) {
            scan_dir(full, asset);
        } else if (entry->d_type == DT_REG || entry->d_type == DT_UNKNOWN) {
            // DT_UNKNOWN: fallback to stat()
            struct stat st;
            if (entry->d_type == DT_UNKNOWN && stat(full.c_str(), &st) == 0) {
                if (S_ISDIR(st.st_mode)) { scan_dir(full, asset); continue; }
                if (!S_ISREG(st.st_mode)) continue;
            }
            g_targets[asset] = full;
            LOGI("  + \"%s\" -> \"%s\"", asset.c_str(), full.c_str());
        }
    }
    closedir(dir);
}

static void load_mod_files(const char* package_name) {
    g_targets.clear();

    char mod_dir[4096];

    // Try external storage first (works for Chinese/Bilibili version).
    snprintf(mod_dir, sizeof(mod_dir),
             "/storage/emulated/0/Android/data/%s/mod", package_name);
    LOGI("Trying mod directory: %s", mod_dir);
    if (mkdir(mod_dir, 0755) == 0) {
        LOGI("Created mod directory: %s", mod_dir);
    }
    scan_dir(mod_dir, "");

    if (g_targets.empty()) {
        // Fallback: app internal files directory (works even with
        // scoped-storage restrictions on Android 11+).
        snprintf(mod_dir, sizeof(mod_dir),
                 "/data/data/%s/files/mod", package_name);
        LOGI("Trying alternative mod directory: %s", mod_dir);
        if (mkdir(mod_dir, 0755) == 0) {
            LOGI("Created mod directory: %s", mod_dir);
        }
        scan_dir(mod_dir, "");
    }

    if (g_targets.empty()) {
        LOGW("No mod files found!");
        LOGW("Chinese version: /storage/emulated/0/Android/data/<pkg>/mod/");
        LOGW("Play Store version: /data/data/<pkg>/files/mod/");
    }
    LOGI("Total: %zu mod file(s) for package \"%s\"", g_targets.size(), package_name);
}

// ─── Proxy asset: for injecting files that don't exist in the APK ───

static AAsset* get_proxy_asset(AAssetManager* mgr, int mode) {
    if (g_proxy_asset_name.empty()) {
        auto openDir = reinterpret_cast<PFN_AAssetManager_openDir>(
            dlsym(RTLD_DEFAULT, "AAssetManager_openDir"));
        auto nextFile = reinterpret_cast<PFN_AAssetDir_getNextFileName>(
            dlsym(RTLD_DEFAULT, "AAssetDir_getNextFileName"));
        auto closeDir = reinterpret_cast<PFN_AAssetDir_close>(
            dlsym(RTLD_DEFAULT, "AAssetDir_close"));

        AAssetDir* dir = openDir ? openDir(mgr, "") : nullptr;
        if (dir) {
            // Skip entries that are in our injection list — they would
            // be returned by getNextFileName first and aren't in the APK.
            const char* name = nullptr;
            while ((name = nextFile ? nextFile(dir) : nullptr)) {
                if (g_targets.find(name) == g_targets.end()) {
                    g_proxy_asset_name = name;
                    break;
                }
            }
            if (closeDir) closeDir(dir);
        }
    }
    if (g_proxy_asset_name.empty()) {
        LOGE("No proxy asset available — APK has zero assets!");
        return nullptr;
    }
    return orig_AAssetManager_open(mgr, g_proxy_asset_name.c_str(), mode);
}

// ─── Hook: AAssetManager_openDir ────────────────────────────────────

static AAssetDir* hook_AAssetManager_openDir(AAssetManager* mgr, const char* dir_name) {
    g_last_mgr = mgr;
    AAssetDir* dir = orig_AAssetManager_openDir(mgr, dir_name);

    if (!dir) return nullptr;

    // Collect injected file names for this directory.
    // De-duplication happens lazily in getNextFileName (skip original
    // entries whose name collides with our injected list). This avoids
    // the eager-read loop that caused trampoline side-effects.
    DirState st;
    std::string prefix = dir_name ? dir_name : "";
    if (!prefix.empty() && prefix.back() != '/') prefix += "/";

    for (const auto& [asset_name, ext_path] : g_targets) {
        std::string candidate;
        if (prefix.empty()) {
            auto slash = asset_name.find('/');
            candidate = (slash != std::string::npos) ? asset_name.substr(0, slash) : asset_name;
        } else {
            if (asset_name.compare(0, prefix.size(), prefix) == 0) {
                std::string rest = asset_name.substr(prefix.size());
                auto slash = rest.find('/');
                candidate = (slash != std::string::npos) ? rest.substr(0, slash) : rest;
            } else {
                continue;
            }
        }
        if (candidate.empty()) continue;

        // Skip if already in injected list
        bool dup = false;
        for (const auto& inj : st.injected) {
            if (inj == candidate) { dup = true; break; }
        }
        if (dup) continue;

        st.injected.push_back(std::move(candidate));
    }

    if (!st.injected.empty()) {
        std::lock_guard<std::mutex> lock(g_dir_mutex);
        g_dir_states[dir] = std::move(st);
    }
    return dir;
}

// ─── Hook: AAssetDir_getNextFileName ────────────────────────────────

static const char* hook_AAssetDir_getNextFileName(AAssetDir* dir) {
    std::lock_guard<std::mutex> lock(g_dir_mutex);
    auto it = g_dir_states.find(dir);
    if (it == g_dir_states.end()) {
        return orig_AAssetDir_getNextFileName(dir);
    }

    DirState& st = it->second;

    if (!st.done_original) {
        // Return original entries, skipping any whose name collides
        // with our injected list (those are handled by AAssetManager_open).
        const char* name;
        while ((name = orig_AAssetDir_getNextFileName(dir)) != nullptr) {
            bool skip = false;
            for (const auto& inj : st.injected) {
                if (inj == name) { skip = true; break; }
            }
            if (!skip) return name;
        }
        st.done_original = true;
    }

    // Return injected entries (only files that DON'T exist in the APK)
    if (st.injected_idx < st.injected.size()) {
        return st.injected[st.injected_idx++].c_str();
    }

    return nullptr;
}

// ─── Hook: AAssetDir_rewind ─────────────────────────────────────────

static void hook_AAssetDir_rewind(AAssetDir* dir) {
    {
        std::lock_guard<std::mutex> lock(g_dir_mutex);
        auto it = g_dir_states.find(dir);
        if (it != g_dir_states.end()) {
            it->second.injected_idx = 0;
            it->second.done_original = false;
        }
    }
    orig_AAssetDir_rewind(dir);
}

// ─── Hook: AAssetDir_close ──────────────────────────────────────────

static void hook_AAssetDir_close(AAssetDir* dir) {
    {
        std::lock_guard<std::mutex> lock(g_dir_mutex);
        g_dir_states.erase(dir);
    }
    orig_AAssetDir_close(dir);
}

// ─── Hook: AAssetManager_open ───────────────────────────────────────

static int g_open_call_count = 0;

static AAsset* hook_AAssetManager_open(AAssetManager* mgr, const char* filename, int mode) {
    if (g_open_call_count < 30) {
        LOGI("[%d] AAssetManager_open(\"%s\", mode=%d)", ++g_open_call_count, filename, mode);
    }

    g_last_mgr = mgr;

    auto it = g_targets.find(filename);
    if (it == g_targets.end()) {
        return orig_AAssetManager_open(mgr, filename, mode);
    }

    const std::string& ext_path = it->second;

    // --- Open external file ---
    int fd = open(ext_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        LOGE("open(\"%s\") failed: %s", ext_path.c_str(), strerror(errno));
        return orig_AAssetManager_open(mgr, filename, mode);
    }

    off_t size = lseek(fd, 0, SEEK_END);
    if (size <= 0) {
        LOGE("External file \"%s\" has zero/negative size (%ld)", ext_path.c_str(), (long)size);
        close(fd);
        return orig_AAssetManager_open(mgr, filename, mode);
    }

    void* data = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (data == MAP_FAILED) {
        LOGE("mmap(\"%s\") failed: %s", ext_path.c_str(), strerror(errno));
        return orig_AAssetManager_open(mgr, filename, mode);
    }

    // --- Try real asset first; if not found, use proxy ---
    AAsset* asset = orig_AAssetManager_open(mgr, filename, mode);
    bool is_proxy = false;

    if (!asset) {
        asset = get_proxy_asset(mgr, mode);
        is_proxy = true;
        if (!asset) {
            LOGE("Cannot create proxy for \"%s\" — asset not in APK and no proxy available", filename);
            munmap(data, size);
            return nullptr;
        }
    }

    AssetReplacement repl;
    repl.data          = static_cast<uint8_t*>(data);
    repl.size          = size;
    repl.offset        = 0;
    repl.external_path = ext_path;
    repl.is_proxy      = is_proxy;

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_replacements[asset] = std::move(repl);
    }

    LOGI("%s asset \"%s\": %zu bytes from \"%s\"",
         is_proxy ? "Injected" : "Replaced",
         filename, size, ext_path.c_str());
    return asset;
}

// ─── Hook: AAsset_read ──────────────────────────────────────────────

static int hook_AAsset_read(AAsset* asset, void* buf, size_t count) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it == g_replacements.end()) {
        return orig_AAsset_read(asset, buf, count);
    }

    AssetReplacement& r = it->second;
    if (r.offset >= static_cast<off_t>(r.size)) return 0;

    size_t remaining = r.size - r.offset;
    size_t n = (count < remaining) ? count : remaining;
    memcpy(buf, r.data + r.offset, n);
    r.offset += n;
    return static_cast<int>(n);
}

// ─── Hook: AAsset_seek ──────────────────────────────────────────────

static off_t hook_AAsset_seek(AAsset* asset, off_t offset, int whence) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it == g_replacements.end()) {
        return orig_AAsset_seek(asset, offset, whence);
    }

    AssetReplacement& r = it->second;
    switch (whence) {
        case SEEK_SET: r.offset = offset; break;
        case SEEK_CUR: r.offset += offset; break;
        case SEEK_END: r.offset = static_cast<off_t>(r.size) + offset; break;
        default: return static_cast<off_t>(-1);
    }
    if (r.offset < 0) r.offset = 0;
    if (r.offset > static_cast<off_t>(r.size)) r.offset = static_cast<off_t>(r.size);
    return r.offset;
}

static off64_t hook_AAsset_seek64(AAsset* asset, off64_t offset, int whence) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it == g_replacements.end()) {
        return orig_AAsset_seek64(asset, offset, whence);
    }

    AssetReplacement& r = it->second;
    switch (whence) {
        case SEEK_SET: r.offset = static_cast<off_t>(offset); break;
        case SEEK_CUR: r.offset += static_cast<off_t>(offset); break;
        case SEEK_END: r.offset = static_cast<off_t>(r.size) + static_cast<off_t>(offset); break;
        default: return static_cast<off64_t>(-1);
    }
    if (r.offset < 0) r.offset = 0;
    if (r.offset > static_cast<off_t>(r.size)) r.offset = static_cast<off_t>(r.size);
    return static_cast<off64_t>(r.offset);
}

// ─── Hook: AAsset_close ─────────────────────────────────────────────

static void hook_AAsset_close(AAsset* asset) {
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_replacements.find(asset);
        if (it != g_replacements.end()) {
            munmap(it->second.data, it->second.size);
            g_replacements.erase(it);
        }
    }
    orig_AAsset_close(asset);
}

// ─── Hook: AAsset_getLength / getLength64 ───────────────────────────

static off_t hook_AAsset_getLength(AAsset* asset) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it != g_replacements.end()) return static_cast<off_t>(it->second.size);
    return orig_AAsset_getLength(asset);
}

static off64_t hook_AAsset_getLength64(AAsset* asset) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it != g_replacements.end()) return static_cast<off64_t>(it->second.size);
    return orig_AAsset_getLength64(asset);
}

// ─── Hook: AAsset_getRemainingLength / getRemainingLength64 ─────────

static off_t hook_AAsset_getRemainingLength(AAsset* asset) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it != g_replacements.end())
        return static_cast<off_t>(it->second.size - it->second.offset);
    return orig_AAsset_getRemainingLength(asset);
}

static off64_t hook_AAsset_getRemainingLength64(AAsset* asset) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it != g_replacements.end())
        return static_cast<off64_t>(it->second.size - it->second.offset);
    return orig_AAsset_getRemainingLength64(asset);
}

// ─── Hook: AAsset_openFileDescriptor / openFileDescriptor64 ─────────

static int hook_AAsset_openFileDescriptor(AAsset* asset, off_t* outStart, off_t* outLength) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it == g_replacements.end()) {
        return orig_AAsset_openFileDescriptor(asset, outStart, outLength);
    }

    const AssetReplacement& r = it->second;
    int fd = open(r.external_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    if (outStart)  *outStart  = 0;
    if (outLength) *outLength = static_cast<off_t>(r.size);
    return fd;
}

static int hook_AAsset_openFileDescriptor64(AAsset* asset, off64_t* outStart, off64_t* outLength) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_replacements.find(asset);
    if (it == g_replacements.end()) {
        return orig_AAsset_openFileDescriptor64(asset, outStart, outLength);
    }

    const AssetReplacement& r = it->second;
    int fd = open(r.external_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    if (outStart)  *outStart  = 0;
    if (outLength) *outLength = static_cast<off64_t>(r.size);
    return fd;
}

// ─── Hook: open() — for Play Asset Delivery (international version) ──
// The international version downloads .pak files via Google's PAD,
// storing them on the filesystem.  The native code reads them with
// plain open() instead of AAssetManager_open.  We hook open() to
// redirect PAD paths → mod directory when the filename matches.

typedef int (*PFN_open)(const char*, int);
static PFN_open orig_open = nullptr;

static int hook_open(const char* pathname, int flags) {
    // Only intercept when we have targets (mod files)
    if (!g_targets.empty() && pathname && strstr(pathname, "assetpacks")) {
        const char* slash = strrchr(pathname, '/');
        if (slash) {
            std::string fname(slash + 1);
            auto it = g_targets.find(fname);
            if (it != g_targets.end()) {
                LOGI("open redirect: %s -> %s", pathname, it->second.c_str());
                return orig_open(it->second.c_str(), flags);
            }
        }
    }
    return orig_open(pathname, flags);
}

// ─── Install all hooks ──────────────────────────────────────────────

static bool install_hooks() {
    // Use RTLD_DEFAULT to search all already-loaded system libraries.
    // This avoids classloader namespace isolation issues where our
    // module .so lives in clns-N and dlopen("libandroid.so", RTLD_NOLOAD) fails.
    #define XHOOK(name) do { \
        void* fn = dlsym(RTLD_DEFAULT, #name); \
        if (fn) { \
            int ret = g_hook(fn, reinterpret_cast<void*>(hook_##name), reinterpret_cast<void**>(&orig_##name)); \
            if (ret == 0) { \
                LOGI("Hooked " #name " @ %p", fn); \
            } else { \
                LOGE("Failed to hook " #name " (ret=%d)", ret); \
            } \
        } else { \
            LOGW(#name " not found via RTLD_DEFAULT: %s", dlerror()); \
        } \
    } while(0)

    XHOOK(AAssetManager_open);
    XHOOK(AAssetManager_openDir);
    XHOOK(AAssetDir_getNextFileName);

    XHOOK(AAsset_read);
    XHOOK(AAsset_seek);
    XHOOK(AAsset_close);
    XHOOK(AAsset_getLength);
    XHOOK(AAsset_openFileDescriptor);

    #undef XHOOK

    // --- open() hook (libc) for Play Asset Delivery redirection ---
    {
        void* fn = dlsym(RTLD_DEFAULT, "open");
        if (fn) {
            int ret = g_hook(fn, reinterpret_cast<void*>(hook_open),
                             reinterpret_cast<void**>(&orig_open));
            if (ret == 0) {
                LOGI("Hooked open @ %p", fn);
            } else {
                LOGE("Failed to hook open (ret=%d)", ret);
            }
        } else {
            LOGW("open not found via RTLD_DEFAULT: %s", dlerror());
        }
    }

    LOGI("All asset hooks installed successfully");
    return true;
}

// ─── JNI: called from Java to set the target package ────────────────

extern "C" JNIEXPORT void JNICALL
Java_com_deadcells_modding_AssetReplacer_nativeInit(
    JNIEnv* env, jclass /*clazz*/, jstring packageName) {

    const char* pkg = env->GetStringUTFChars(packageName, nullptr);
    LOGI("Initializing AssetReplacer for package: %s", pkg);
    load_mod_files(pkg);
    env->ReleaseStringUTFChars(packageName, pkg);
}

// ─── Library loaded callback (unused) ───────────────────────────────

static void on_library_loaded(const char* /*name*/, void* /*handle*/) {
}

// ─── LSPosed native_init entry point ────────────────────────────────

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    g_hook = entries->hook_func;
    LOGI("native_init called, installing hooks...");
    install_hooks();
    return on_library_loaded;
}
