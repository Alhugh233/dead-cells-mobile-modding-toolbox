#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cerrno>
#include <string>
#include <vector>
#include <functional>
#include <algorithm>
#include <sys/stat.h>
#include <dirent.h>
#include <jni.h>
#include "log_bridge.h"
#include "stb_image.h"
#include "stb_image_write.h"

/* ─── Little-endian I/O ───────────────────────────────────────────── */

static inline uint32_t u32(const uint8_t* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}
static inline void w32(uint8_t* p, uint32_t v) {
    p[0] = v; p[1] = v >> 8; p[2] = v >> 16; p[3] = v >> 24;
}
static uint32_t read_u32(const uint8_t*& p) {
    uint32_t v = u32(p); p += 4; return v;
}
static void write_u32(std::vector<uint8_t>& out, uint32_t v) {
    uint8_t b[4]; w32(b, v);
    out.insert(out.end(), b, b + 4);
}

/* ─── Adler-32 ────────────────────────────────────────────────────── */

static uint32_t adler32(const uint8_t* d, size_t n) {
    uint32_t a = 1, b = 0;
    for (size_t i = 0; i < n; i++) { a = (a + d[i]) % 65521; b = (b + a) % 65521; }
    return (b << 16) | a;
}

/* ─── Name encoding: the original Dead Cells engine (HashLink/Haxe) ─
   writes names as plain 1-byte-per-char ASCII. The PC mod loader
   (C# BinaryWriter) uses 2-byte UTF-16. We support both by checking
   the encoding byte after the name length.                        */

static std::string read_name(const uint8_t*& p, bool utf16) {
    uint32_t len = *p++;
    if (len == 0xFF) { len = (uint32_t)*p | ((uint32_t)*(p+1) << 8); p += 2; }
    std::string s;
    for (int i = 0; i < len; i++) {
        if (utf16) {
            char16_t ch = (char16_t)*p | ((char16_t)*(p + 1) << 8);
            p += 2;
            s += (ch < 128) ? (char)ch : '?';
        } else {
            s += (char)*p++;
        }
    }
    return s;
}
static void write_name(std::vector<uint8_t>& out, const std::string& s) {
    out.push_back((uint8_t)s.size());
    for (char c : s) out.push_back((uint8_t)c);
}

/* ─── PAK entry tree node ────────────────────────────────────────── */

struct PakNode {
    std::string name;
    bool is_dir = false;
    std::vector<PakNode> children;
    // file fields
    uint64_t data_off = 0;
    uint32_t data_len = 0;
    uint32_t checksum = 0;
    std::vector<uint8_t> data; // only used during repack
};

/* ─── Directory helpers ───────────────────────────────────────────── */

static bool mkdirs(const std::string& path) {
    size_t pos = 0;
    while ((pos = path.find('/', pos + 1)) != std::string::npos) {
        std::string sub = path.substr(0, pos);
        mkdir(sub.c_str(), 0755);
    }
    return mkdir(path.c_str(), 0755) == 0 || errno == EEXIST;
}

static void collect_files(const std::string& base, PakNode* root) {
    DIR* d = opendir(base.c_str());
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (e->d_name[0] == '.') continue;
        if (strcmp(e->d_name, "stamp.txt") == 0) continue; // skip stamp file
        std::string path = base + "/" + e->d_name;
        if (e->d_type == DT_DIR) {
            PakNode dir;
            dir.name = e->d_name;
            dir.is_dir = true;
            collect_files(path, &dir);
            root->children.push_back(std::move(dir));
        } else if (e->d_type == DT_REG || e->d_type == DT_UNKNOWN) {
            struct stat st;
            if (e->d_type == DT_UNKNOWN) stat(path.c_str(), &st);
            FILE* f = fopen(path.c_str(), "rb");
            if (!f) continue;
            fseek(f, 0, SEEK_END);
            size_t sz = ftell(f);
            rewind(f);
            PakNode file;
            file.name = e->d_name;
            file.is_dir = false;
            file.data_len = (uint32_t)sz;
            file.data.resize(sz);
            fread(file.data.data(), 1, sz, f);
            fclose(f);
            file.checksum = adler32(file.data.data(), sz);
            root->children.push_back(std::move(file));
        }
    }
    closedir(d);
}

/* ─── PAK reader ─────────────────────────────────────────────────── */

static bool parse_entry(const uint8_t*& p, const uint8_t* end, PakNode* node) {
    if (p >= end) return false;
    node->name = read_name(p, false); // game PAKs use 1-byte ASCII names
    if (p >= end) return false;
    uint8_t kind = *p++;
    if (kind == 1) {
        node->is_dir = true;
        if (p + 4 > end) return false;
        uint32_t n = read_u32(p);
        for (uint32_t i = 0; i < n; i++) {
            PakNode child;
            if (!parse_entry(p, end, &child)) return false;
            node->children.push_back(std::move(child));
        }
    } else {
        node->is_dir = false;
        bool use64 = (kind & 2) != 0;
        if (p + (use64 ? 16 : 12) > end) return false;
        if (use64) {
            uint32_t lo = read_u32(p);
            uint32_t hi = read_u32(p);
            node->data_off = (uint64_t)lo | ((uint64_t)hi << 32);
        } else {
            node->data_off = read_u32(p);
        }
        node->data_len = read_u32(p);
        node->checksum = read_u32(p);
    }
    return true;
}

static bool pak_unpack(const char* pak_path, const char* out_dir) {
    FILE* f = fopen(pak_path, "rb");
    if (!f) { LOGE_PAK("Cannot open: %s (%s)", pak_path, strerror(errno)); return false; }
    fseek(f, 0, SEEK_END);
    size_t len = ftell(f);
    rewind(f);
    std::vector<uint8_t> buf(len);
    fread(buf.data(), 1, len, f);
    fclose(f);

    if (len < 12) { LOGE_PAK("File too small (%zu bytes)", len); return false; }
    if (memcmp(buf.data(), "PAK", 3) != 0) {
        LOGE_PAK("Not a PAK file (magic mismatch: 0x%02X%02X%02X)",
                 buf[0], buf[1], buf[2]);
        return false;
    }
    uint8_t ver = buf[3];
    uint32_t hdr_sz = u32(buf.data() + 4);
    uint32_t dat_sz = u32(buf.data() + 8);

    // Save stamp to stamp.txt (after directory is created below) — placeholder
    bool has_stamp_save = (ver == 1 && len >= 76);
    uint8_t stamp_bytes[64];
    if (has_stamp_save) memcpy(stamp_bytes, buf.data() + 12, 64);

    const uint8_t* p = buf.data() + 12;
    if (ver == 1) p += 64;
    const uint8_t* end = buf.data() + len;

    // Dump first 256 bytes of entry tree for debugging
    size_t tree_start = (size_t)(p - buf.data());
    char hex[512] = {};
    int off = 0;
    for (int i = 0; i < 128 && (tree_start + i) < len; i++) {
        off += snprintf(hex + off, sizeof(hex) - off, "%02X ", buf[tree_start + i]);
    }
    LOGI_PAK("Entry tree at %zu: %s", tree_start, hex);
    // Also dump bytes near expected DATA location (headerSize)
    LOGI_PAK("Bytes near headerSize %u: %02X %02X %02X %02X  %02X %02X %02X %02X",
             hdr_sz,
             buf[hdr_sz-8], buf[hdr_sz-7], buf[hdr_sz-6], buf[hdr_sz-5],
             buf[hdr_sz-4], buf[hdr_sz-3], buf[hdr_sz-2], buf[hdr_sz-1]);

    LOGI_PAK("PAK v%d, header=%u, data=%u, %zu bytes total", ver, hdr_sz, dat_sz, len);

    PakNode root;
    if (!parse_entry(p, end, &root)) {
        LOGE_PAK("Failed to parse PAK entry tree at offset %td", p - buf.data());
        return false;
    }

    size_t data_ofs = (size_t)(p - buf.data());
    if (data_ofs + 4 > len || memcmp(buf.data() + data_ofs, "DATA", 4) != 0) {
        LOGE_PAK("DATA marker not found at offset %zu", data_ofs);
        return false;
    }
    const uint8_t* data_ptr = buf.data() + data_ofs + 4;

    mkdirs(out_dir);

    // Write stamp file (now that directory exists)
    if (has_stamp_save) {
        std::string stamp_path = std::string(out_dir) + "/stamp.txt";
        FILE* sf = fopen(stamp_path.c_str(), "wb");
        if (sf) {
            fwrite(stamp_bytes, 1, 64, sf);
            fclose(sf);
            LOGI_PAK("Stamp saved to %s", stamp_path.c_str());
        } else {
            LOGE_PAK("Cannot write stamp: %s", stamp_path.c_str());
        }
    }

    int file_count = 0;
    std::function<void(const PakNode&, const std::string&)> extract;
    extract = [&](const PakNode& node, const std::string& base) {
        std::string path = base + "/" + node.name;
        if (node.is_dir) {
            mkdir(path.c_str(), 0755);
            for (auto& c : node.children) extract(c, path);
        } else {
            LOGI_PAK("  extract %s (%u bytes)", path.c_str(), node.data_len);
            FILE* o = fopen(path.c_str(), "wb");
            if (o) {
                fwrite(data_ptr + node.data_off, 1, node.data_len, o);
                fclose(o);
                file_count++;
            } else {
                LOGE_PAK("  cannot write %s (%s)", path.c_str(), strerror(errno));
            }
        }
    };
    for (auto& c : root.children) extract(c, out_dir);
    LOGI_PAK("Unpack complete: %d files extracted", file_count);
    return true;
}

/* ─── PAK writer ─────────────────────────────────────────────────── */

static void write_tree(PakNode* node, std::vector<uint8_t>& hdr,
                       std::vector<uint8_t>& dat, uint64_t* data_ofs) {
    if (node->is_dir) {
        write_name(hdr, node->name);
        hdr.push_back(1);
        write_u32(hdr, (uint32_t)node->children.size());
        for (auto& c : node->children) write_tree(&c, hdr, dat, data_ofs);
    } else {
        write_name(hdr, node->name);
        hdr.push_back(0);
        write_u32(hdr, (uint32_t)*data_ofs);       // 32-bit offset, no 64-bit support
        write_u32(hdr, node->data_len);
        write_u32(hdr, node->checksum);
        *data_ofs += node->data_len;
        dat.insert(dat.end(), node->data.begin(), node->data.end());
    }
}

static bool pak_pack(const char* in_dir, const char* out_pak, const char* stamp) {
    PakNode root;
    root.is_dir = true;
    collect_files(in_dir, &root);

    // Auto-detect stamp from stamp.txt
    char stamp_buf[64] = {};
    if (!stamp) {
        std::string stamp_path = std::string(in_dir) + "/stamp.txt";
        FILE* sf = fopen(stamp_path.c_str(), "rb");
        if (sf) { fread(stamp_buf, 1, 64, sf); fclose(sf); stamp = stamp_buf; }
    }
    bool has_stamp = stamp && stamp[0] != 0;

    std::vector<uint8_t> hdr, dat;
    hdr.insert(hdr.end(), {'P', 'A', 'K'});
    hdr.push_back(has_stamp ? 1 : 0);
    // placeholder for headerSize and dataSize
    write_u32(hdr, 0);
    write_u32(hdr, 0);
    if (has_stamp) {
        uint8_t st[64] = {};
        memcpy(st, stamp, 64);
        hdr.insert(hdr.end(), st, st + 64);
    }

    // Write root directory entry (name="", kind=1, children_count)
    hdr.push_back(0);  // name length = 0 (empty name for root)
    hdr.push_back(1);  // kind = 1 (directory)
    write_u32(hdr, (uint32_t)root.children.size());
    root.name = "";
    root.is_dir = true;

    uint64_t data_ofs = 0;
    for (auto& c : root.children) write_tree(&c, hdr, dat, &data_ofs);

    hdr.insert(hdr.end(), {'D', 'A', 'T', 'A'});

    // back-patch header size and data size
    uint32_t hdr_sz = (uint32_t)hdr.size();
    uint32_t dat_sz = (uint32_t)dat.size();
    w32(hdr.data() + 4, hdr_sz);
    w32(hdr.data() + 8, dat_sz);

    FILE* f = fopen(out_pak, "wb");
    if (!f) return false;
    fwrite(hdr.data(), 1, hdr.size(), f);
    fwrite(dat.data(), 1, dat.size(), f);
    fclose(f);
    return true;
}

/* ─── Atlas (BATL) unpack ───────────────────────────────────────── */

struct AtlasSprite {
    std::string name;
    uint16_t idx, x, y, w, h, offx, offy, ow, oh;
};

struct AtlasGroup {
    std::string name;
    std::vector<AtlasSprite> sprites;
};

static std::string read_cstring(const uint8_t*& p, const uint8_t* end) {
    std::string s;
    while (p < end && *p) s += (char)*p++;
    if (p < end) p++; // skip null terminator
    return s;
}

static uint16_t read_u16(const uint8_t*& p) {
    uint16_t v = (uint16_t)*p | ((uint16_t)*(p+1) << 8);
    p += 2;
    return v;
}

static bool atlas_unpack(const char* atlas_path, const char* out_dir) {
    FILE* f = fopen(atlas_path, "rb");
    if (!f) { LOGE_PAK("Cannot open atlas: %s", atlas_path); return false; }
    fseek(f, 0, SEEK_END);
    size_t len = ftell(f);
    rewind(f);
    std::vector<uint8_t> buf(len);
    fread(buf.data(), 1, len, f);
    fclose(f);

    if (len < 4 || memcmp(buf.data(), "BATL", 4) != 0) {
        LOGE_PAK("Not a BATL atlas file (magic mismatch)");
        return false;
    }

    const uint8_t* p = buf.data() + 4;
    const uint8_t* end = buf.data() + len;

    std::vector<AtlasGroup> groups;

    while (p < end) {
        // Atlas names are length-prefixed (1 or 3 bytes), same as PAK names
        std::string atlas_name = read_name(p, false);
        if (atlas_name.empty()) break;
        LOGI_PAK("  group: '%s'", atlas_name.c_str());
        AtlasGroup grp;
        grp.name = atlas_name;
        while (p < end) {
            // Sprite names are also length-prefixed
            std::string sprite_name = read_name(p, false);
            if (sprite_name.empty()) break;
            std::string base = sprite_name;
            if (p + 18 > end) { LOGE_PAK("Truncated sprite entry"); return false; }
            AtlasSprite sp;
            sp.name = base;  // keep original name with _idx suffix
            sp.idx  = read_u16(p);
            sp.x    = read_u16(p);
            sp.y    = read_u16(p);
            sp.w    = read_u16(p);
            sp.h    = read_u16(p);
            sp.offx = read_u16(p);
            sp.offy = read_u16(p);
            sp.ow   = read_u16(p);
            sp.oh   = read_u16(p);
            grp.sprites.push_back(sp);
        }
        groups.push_back(std::move(grp));
    }

    mkdirs(out_dir);

    // Derive atlas texture directory (same dir as .atlas file)
    std::string atlas_dir = atlas_path;
    auto slash = atlas_dir.rfind('/');
    if (slash != std::string::npos) atlas_dir = atlas_dir.substr(0, slash);
    else atlas_dir = ".";

    for (auto& grp : groups) {
        // --- Save coordinates text (always) ---
        std::string txt_path = std::string(out_dir) + "/" + grp.name + ".txt";
        FILE* out = fopen(txt_path.c_str(), "w");
        if (out) {
            fprintf(out, "texture: %s.png\n", grp.name.c_str());
            fprintf(out, "sprites: %zu\n\n", grp.sprites.size());
            for (auto& sp : grp.sprites) {
                fprintf(out, "%-40s  idx=%-4u  xy=(%5u,%5u)  wh=(%4u,%4u)  "
                        "off=(%4d,%4d)  real=(%4u,%4u)\n",
                        sp.name.c_str(), sp.idx,
                        sp.x, sp.y, sp.w, sp.h,
                        sp.offx, sp.offy, sp.ow, sp.oh);
            }
            fclose(out);
        }

        // --- Extract individual sprites as PNGs ---
        std::string png_path = atlas_dir + "/" + grp.name;
        LOGI_PAK("  looking for texture: %s", png_path.c_str());
        // Check if file exists before attempting stbi_load
        FILE* check = fopen(png_path.c_str(), "rb");
        if (!check) {
            LOGE_PAK("  texture not found: %s", png_path.c_str());
            continue;
        }
        fclose(check);
        int tw = 0, th = 0, tc = 0;
        uint8_t* tex_data = stbi_load(png_path.c_str(), &tw, &th, &tc, 4);
        if (!tex_data) {
            LOGE_PAK("  failed to load texture: %s", png_path.c_str());
            continue;
        }
            std::string sprite_dir = std::string(out_dir) + "/" + grp.name;
            // Strip .png suffix from group name for directory
            if (sprite_dir.size() > 4 && sprite_dir.substr(sprite_dir.size() - 4) == ".png")
                sprite_dir = sprite_dir.substr(0, sprite_dir.size() - 4);
            mkdirs(sprite_dir);
            for (auto& sp : grp.sprites) {
                if (sp.x + sp.w > (uint16_t)tw || sp.y + sp.h > (uint16_t)th) continue;
                // Extract sprite pixels (with real-size canvas for proper positioning)
                int bw = (int)sp.ow, bh = (int)sp.oh;  // real (output) size
                if (bw == 0 || bh == 0) { bw = sp.w; bh = sp.h; }
                std::vector<uint8_t> out_pixels(bw * bh * 4, 0);
                // Copy trimmed rect from atlas into output canvas at (offx, offy)
                for (int row = 0; row < (int)sp.h; row++) {
                    int src_row = (int)sp.y + row;
                    int dst_row = (int)sp.offy + row;
                    if (dst_row >= bh) break;
                    memcpy(out_pixels.data() + (dst_row * bw + (int)sp.offx) * 4,
                           tex_data + (src_row * tw + (int)sp.x) * 4,
                           (int)sp.w * 4);
                }
                std::string spr_path = sprite_dir + "/" + sp.name + ".png";
                stbi_write_png(spr_path.c_str(), bw, bh, 4, out_pixels.data(), bw * 4);
                LOGI_PAK("  sprite: %s (%dx%d)", spr_path.c_str(), bw, bh);
            }
            stbi_image_free(tex_data);
    }
    LOGI_PAK("Atlas unpack complete: %zu groups", groups.size());
    return true;
}

/* ─── PAK merge ─────────────────────────────────────────────────── */

static void merge_node(PakNode* dst, const PakNode& src) {
    if (src.is_dir) {
        // Find or create directory
        PakNode* dd = nullptr;
        for (auto& c : dst->children) {
            if (c.is_dir && c.name == src.name) { dd = &c; break; }
        }
        if (!dd) {
            dst->children.push_back({});
            dd = &dst->children.back();
            dd->name = src.name;
            dd->is_dir = true;
        }
        for (auto& sc : src.children) merge_node(dd, sc);
    } else {
        // Overwrite or add file
        PakNode* fe = nullptr;
        for (auto& c : dst->children) {
            if (!c.is_dir && c.name == src.name) { fe = &c; break; }
        }
        if (!fe) {
            dst->children.push_back({});
            fe = &dst->children.back();
            fe->name = src.name;
        }
        fe->is_dir = false;
        fe->data = src.data;
        fe->data_len = src.data_len;
        fe->checksum = src.checksum;
    }
}

static bool pak_merge(const char* out_pak, const char* stamp, int count, const char** inputs) {
    PakNode root;
    root.is_dir = true;

    // Auto-detect stamp from first input's directory
    char stamp_buf[64] = {};
    if (!stamp && count > 0) {
        // Read stamp directly from first input PAK header
        LOGI_PAK("Attempting stamp read from: %s", inputs[0]);
        FILE* f = fopen(inputs[0], "rb");
        if (f) {
            uint8_t hdr[76];
            size_t rd = fread(hdr, 1, 76, f);
            LOGI_PAK("Read %zu bytes, version byte = %d", rd, (int)hdr[3]);
            if (rd >= 76 && hdr[3] == 1) {
                memcpy(stamp_buf, hdr + 12, 64);
                stamp = stamp_buf;
                LOGI_PAK("Stamp copied from first PAK");
            } else {
                LOGI_PAK("No stamp (ver=%d, rd=%zu)", (int)hdr[3], rd);
            }
            fclose(f);
        } else {
            LOGE_PAK("Cannot open first PAK for stamp: %s", inputs[0]);
        }
    }
    bool has_stamp = stamp && stamp[0] != 0;
    if (has_stamp) LOGI_PAK("Merge: using auto-detected stamp");

    for (int i = 0; i < count; i++) {
        LOGI_PAK("Merging: %s", inputs[i]);
        FILE* f = fopen(inputs[i], "rb");
        if (!f) { LOGE_PAK("Cannot open: %s", inputs[i]); return false; }
        fseek(f, 0, SEEK_END); size_t len = ftell(f); rewind(f);
        std::vector<uint8_t> buf(len);
        fread(buf.data(), 1, len, f); fclose(f);

        if (len < 12 || memcmp(buf.data(), "PAK", 3) != 0) {
            LOGE_PAK("Not a PAK file: %s", inputs[i]); return false;
        }
        uint8_t ver = buf[3];
        uint32_t hdr_sz = u32(buf.data() + 4);
        const uint8_t* p = buf.data() + 12;
        if (ver == 1) p += 64;
        const uint8_t* end = buf.data() + len;
        const uint8_t* data_ptr = buf.data() + hdr_sz; // headerSize already points past DATA

        PakNode src_root;
        if (!parse_entry(p, end, &src_root)) return false;

        // Verify: actual DATA position should match headerSize
        size_t parsed_data = (size_t)(p - buf.data());
        LOGI_PAK("  hdr_sz=%u, actual=%zu, DATA=%s",
                 hdr_sz, parsed_data,
                 (parsed_data == hdr_sz) ? "MATCH" : "MISMATCH");

        // Fill data from the data block
        std::function<void(PakNode&)> fill = [&](PakNode& node) {
            if (!node.is_dir) {
                node.data.resize(node.data_len);
                memcpy(node.data.data(), data_ptr + node.data_off, node.data_len);
            }
            for (auto& c : node.children) fill(c);
        };
        fill(src_root);

        // Merge into root
        for (auto& c : src_root.children) merge_node(&root, c);
    }

    // Write merged PAK — sort entries for filesystem-order output
    std::function<void(PakNode&)> sort_tree = [&](PakNode& node) {
        std::sort(node.children.begin(), node.children.end(),
            [](const PakNode& a, const PakNode& b) { return a.name < b.name; });
        for (auto& c : node.children) sort_tree(c);
    };
    sort_tree(root);

    std::vector<uint8_t> hdr, dat;
    hdr.insert(hdr.end(), {'P', 'A', 'K'});
    hdr.push_back(has_stamp ? 1 : 0);
    write_u32(hdr, 0);
    write_u32(hdr, 0);
    if (has_stamp) {
        uint8_t st[64] = {};
        memcpy(st, stamp, 64);
        hdr.insert(hdr.end(), st, st + 64);
    }

    // Root directory entry
    hdr.push_back(0);
    hdr.push_back(1);
    write_u32(hdr, (uint32_t)root.children.size());

    uint64_t data_ofs = 0;
    for (auto& c : root.children) write_tree(&c, hdr, dat, &data_ofs);

    hdr.insert(hdr.end(), {'D', 'A', 'T', 'A'});
    w32(hdr.data() + 4, (uint32_t)hdr.size());
    w32(hdr.data() + 8, (uint32_t)dat.size());

    FILE* f = fopen(out_pak, "wb");
    if (!f) return false;
    fwrite(hdr.data(), 1, hdr.size(), f);
    fwrite(dat.data(), 1, dat.size(), f);
    fclose(f);
    LOGI_PAK("Merge complete: %s", out_pak);
    return true;
}

/* ─── JNI wrappers ────────────────────────────────────────────────── */

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_deadcells_modding_PakTool_unpack(JNIEnv* env, jclass,
                                                 jstring pakPath, jstring outDir) {
    const char* pak = env->GetStringUTFChars(pakPath, nullptr);
    const char* out = env->GetStringUTFChars(outDir, nullptr);
    LOGI_PAK("unpack: %s -> %s", pak, out);
    bool ok = pak_unpack(pak, out);
    env->ReleaseStringUTFChars(pakPath, pak);
    env->ReleaseStringUTFChars(outDir, out);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_deadcells_modding_PakTool_pack(JNIEnv* env, jclass,
                                               jstring inDir, jstring outPak, jstring stamp) {
    const char* in = env->GetStringUTFChars(inDir, nullptr);
    const char* out = env->GetStringUTFChars(outPak, nullptr);
    const char* st = stamp ? env->GetStringUTFChars(stamp, nullptr) : nullptr;
    LOGI_PAK("pack: %s -> %s", in, out);
    bool ok = pak_pack(in, out, st);
    env->ReleaseStringUTFChars(inDir, in);
    env->ReleaseStringUTFChars(outPak, out);
    if (st) env->ReleaseStringUTFChars(stamp, st);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_deadcells_modding_PakTool_atlasUnpack(JNIEnv* env, jclass,
                                                      jstring atlasPath, jstring outDir) {
    const char* ap = env->GetStringUTFChars(atlasPath, nullptr);
    const char* od = env->GetStringUTFChars(outDir, nullptr);
    LOGI_PAK("atlasUnpack: %s -> %s", ap, od);
    bool ok = atlas_unpack(ap, od);
    env->ReleaseStringUTFChars(atlasPath, ap);
    env->ReleaseStringUTFChars(outDir, od);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_deadcells_modding_PakTool_merge(JNIEnv* env, jclass,
                                                 jstring outPak, jstring stamp, jobjectArray inputs) {
    const char* out = env->GetStringUTFChars(outPak, nullptr);
    const char* st = stamp ? env->GetStringUTFChars(stamp, nullptr) : nullptr;
    int count = env->GetArrayLength(inputs);
    std::vector<const char*> in_ptrs(count);
    std::vector<jstring> in_strs(count);
    for (int i = 0; i < count; i++) {
        in_strs[i] = (jstring)env->GetObjectArrayElement(inputs, i);
        in_ptrs[i] = env->GetStringUTFChars(in_strs[i], nullptr);
    }
    LOGI_PAK("merge: %d inputs -> %s", count, out);
    bool ok = pak_merge(out, st, count, in_ptrs.data());
    for (int i = 0; i < count; i++) env->ReleaseStringUTFChars(in_strs[i], in_ptrs[i]);
    env->ReleaseStringUTFChars(outPak, out);
    if (st) env->ReleaseStringUTFChars(stamp, st);
    return ok ? JNI_TRUE : JNI_FALSE;
}

}
