#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image.h"
#include "stb_image_write.h"

#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>
#include <algorithm>
#include <dirent.h>
#include <sys/stat.h>
#include "log_bridge.h"

/* ─── Rectangle packing (shelf algorithm) ────────────────────────── */

struct Rect {
    int x = 0, y = 0, w = 0, h = 0;
    int id = -1; // index into source array
};

// Simple shelf packer: places rects left-to-right on shelves from top to bottom.
// Sorts input by height descending for better packing.
static bool pack_rects(std::vector<Rect>& rects, int max_w, int* out_w, int* out_h) {
    if (rects.empty()) return false;
    // Sort by height desc
    std::sort(rects.begin(), rects.end(), [](const Rect& a, const Rect& b) {
        return a.h > b.h || (a.h == b.h && a.w > b.w);
    });

    int shelf_y = 0;
    int shelf_h = 0;
    int cur_x = 0;
    int total_w = 0;
    int total_h = 0;

    for (auto& r : rects) {
        if (r.w > max_w) return false; // won't fit

        if (cur_x + r.w > max_w || (shelf_h > 0 && r.h != shelf_h)) {
            // New shelf
            shelf_y += shelf_h;
            cur_x = 0;
            shelf_h = 0;
        }

        if (shelf_h == 0) shelf_h = r.h;

        r.x = cur_x;
        r.y = shelf_y;
        cur_x += r.w;
        total_w = std::max(total_w, cur_x);
        total_h = std::max(total_h, shelf_y + shelf_h);
    }

    *out_w = total_w;
    *out_h = total_h;
    return true;
}

/* ─── Atlas pack: read PNGs → pack → write atlas PNG + BATL ──────── */

struct InputImage {
    std::string name; // basename without extension
    std::string path;
    uint8_t* data = nullptr;
    int w = 0, h = 0, channels = 0;
};

static bool atlas_pack(const char* input_dir, const char* out_atlas, const char* out_png) {
    // --- 1. Read all PNG files from input directory ---
    DIR* d = opendir(input_dir);
    if (!d) { LOGE_ATL("Cannot open: %s", input_dir); return false; }
    std::vector<InputImage> images;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        std::string name(e->d_name);
        if (name.size() < 4) continue;
        if (name.substr(name.size() - 4) != ".png") continue;
        std::string path = std::string(input_dir) + "/" + name;
        InputImage img;
        img.name = name.substr(0, name.size() - 4);
        img.path = path;
        img.data = stbi_load(path.c_str(), &img.w, &img.h, &img.channels, 4); // force RGBA
        if (!img.data) continue;
        images.push_back(img);
    }
    closedir(d);
    if (images.empty()) { LOGE_ATL("No PNG files found"); return false; }
    LOGI_ATL("Loaded %zu images", images.size());

    // --- 2. Create packing rectangles ---
    std::vector<Rect> rects;
    for (size_t i = 0; i < images.size(); i++) {
        Rect r;
        r.w = images[i].w;
        r.h = images[i].h;
        r.id = (int)i;
        rects.push_back(r);
    }

    // --- 3. Pack ---
    int atlas_w = 2048; // starting width, will try larger if needed
    int atlas_h = 0;
    int actual_w = 0, actual_h = 0;
    static const int sizes[] = {2048, 4096, 8192, 0};
    for (int si = 0; sizes[si]; si++) {
        auto tmp = rects;
        if (pack_rects(tmp, sizes[si], &actual_w, &actual_h)) {
            rects = tmp;
            atlas_w = sizes[si];
            break;
        }
    }
    if (actual_w == 0) { LOGE_ATL("Packing failed"); return false; }
    // Make height power-of-two for GPU compatibility
    int pot = 1;
    while (pot < actual_h) pot <<= 1;
    atlas_h = pot;
    LOGI_ATL("Atlas: %dx%d, %zu sprites", actual_w, atlas_h, images.size());

    // --- 4. Build atlas RGBA buffer ---
    std::vector<uint8_t> atlas_rgba(atlas_w * atlas_h * 4, 0);
    for (auto& r : rects) {
        auto& img = images[r.id];
        for (int row = 0; row < r.h; row++) {
            uint8_t* src = img.data + row * r.w * 4;
            uint8_t* dst = atlas_rgba.data() + ((r.y + row) * atlas_w + r.x) * 4;
            memcpy(dst, src, r.w * 4);
        }
        stbi_image_free(img.data);
        img.data = nullptr;
    }

    // --- 5. Write atlas PNG ---
    if (!stbi_write_png(out_png, atlas_w, atlas_h, 4, atlas_rgba.data(), atlas_w * 4)) {
        LOGE_ATL("Failed to write atlas PNG: %s", out_png);
        return false;
    }
    LOGI_ATL("Wrote atlas PNG: %s", out_png);

    // --- 6. Write BATL file ---
    FILE* batl = fopen(out_atlas, "wb");
    if (!batl) { LOGE_ATL("Cannot write: %s", out_atlas); return false; }
    fwrite("BATL", 1, 4, batl);

    // One group per atlas (single atlas for now)
    std::string aname = "atlas";
    fwrite(aname.c_str(), 1, aname.size() + 1, batl); // null-term

    for (auto& r : rects) {
        auto& img = images[r.id];
        // Sprite name (no trailing index)
        fwrite(img.name.c_str(), 1, img.name.size() + 1, batl);

        // idx: u16
        uint8_t idx[2] = {0, 0};
        fwrite(idx, 1, 2, batl);
        // x, y, w, h
        auto w16 = [&](int v) { uint8_t b[2] = {(uint8_t)v, (uint8_t)(v>>8)}; fwrite(b, 1, 2, batl); };
        w16(r.x); w16(r.y); w16(r.w); w16(r.h);
        // offx, offy, ow, oh (for now, same as trimmed = no offset)
        w16(0); w16(0); w16(r.w); w16(r.h);
    }
    // End of sprites (empty name)
    fputc(0, batl);
    // End of groups (empty name)
    fputc(0, batl);
    fclose(batl);
    LOGI_ATL("Wrote BATL: %s", out_atlas);

    return true;
}

/* ─── JNI wrapper ─────────────────────────────────────────────────── */

#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_libxposed_example_PakTool_atlasPack(JNIEnv* env, jclass,
                                                     jstring inDir, jstring outAtlas, jstring outPng) {
    const char* dir = env->GetStringUTFChars(inDir, nullptr);
    const char* atl = env->GetStringUTFChars(outAtlas, nullptr);
    const char* png = env->GetStringUTFChars(outPng, nullptr);
    LOGI_ATL("atlasPack: %s -> %s + %s", dir, atl, png);
    bool ok = atlas_pack(dir, atl, png);
    env->ReleaseStringUTFChars(inDir, dir);
    env->ReleaseStringUTFChars(outAtlas, atl);
    env->ReleaseStringUTFChars(outPng, png);
    return ok ? JNI_TRUE : JNI_FALSE;
}
