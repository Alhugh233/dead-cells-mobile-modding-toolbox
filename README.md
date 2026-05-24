# Dead Cells Mobile Modding Toolbox

> [中文 / Chinese](README_CN.md)

A multi-purpose modding toolbox for Dead Cells Mobile (Android), built as an [LSPosed](https://github.com/LSPosed/LSPosed) module based on the [libxposed/example](https://github.com/libxposed/example) template. It doubles as a standalone PAK/Atlas toolkit that works without root.

> **Disclaimer:** This project was created via vibe coding (AI-assisted development). Code quality may vary. Use at your own risk.

## Features

### Asset Injection (Runtime — requires LSPosed)

| Feature | Description | Status |
|---------|-------------|--------|
| Asset replacement | Replace existing `.pak` files with modded versions | ✅ Working |
| Asset injection | Inject new `.pak` files that don't exist in the APK | ❌ Game ignores injected files |
| PAD path redirection | Support for Google Play Asset Delivery paths (global version) | ✅ Working |
| Native hook | Hooks `AAssetManager_open` + `open()` syscall via LSPlant | ✅ Working |

### PAK Toolkit (Offline — no root required)

| Feature | Description | Status |
|---------|-------------|--------|
| PAK unpack | Extract `.pak` files to directory tree | ✅ Working |
| PAK pack | Build `.pak` from directory (auto stamp preservation) | ✅ Working |
| PAK merge | Merge multiple `.pak` into one (overwrite logic) | ✅ Working |
| Atlas unpack | Extract `.atlas` (BATL) to individual PNG sprites + coordinates | ⚠️ Experimental |
| Atlas pack | Build `.atlas` + atlas PNG from individual sprites | ⚠️ Experimental |
| MO ↔ PO | Convert MO files to/from PO (gettext) | ❌ Removed — game uses non-standard format |

### Supported Versions

| Platform | Package ID | Status |
|----------|-----------|--------|
| Bilibili (China) | `com.bilibili.deadcells.mobile` | ✅ Working |
| Google Play (Global) | `com.playdigious.deadcells.mobile` | ✅ Working |

> **⚠️ Google Play version note:** The official Google Play build includes **pairip** (Play Integrity / anti-tamper). If you are using the Google Play version, you must enable **"Invalidate inline hooks"** for Dead Cells in LSPosed's module settings. Otherwise pairip will detect the modification and crash the game.
>
> ![](docs/pairip_setting_1.jpg)
> ![](docs/pairip_setting_2.jpg)

## How It Works

The module uses a two-layer hooking approach:

1. **Java layer** — Hooks the game's `DeadCells.onStart()` → `Assets.init()` chain to intercept the `AssetManager` before it's passed to native code
2. **Native layer** — Hooks `AAssetManager_open`, `AAssetManager_openDir`, `AAssetDir_getNextFileName`, `AAsset_read`, `AAsset_seek`, `AAsset_close`, `AAsset_getLength`, `AAsset_openFileDescriptor` in `libandroid.so`, plus `open()` in `libc.so`

Mod files go to:
- Bilibili: `/storage/emulated/0/Android/data/com.bilibili.deadcells.mobile/mod/`
- Google Play: `/data/data/com.playdigious.deadcells.mobile/files/mod/`
  (external storage is not accessible due to scoped storage restrictions)

## Building

1. Install Android SDK with `platforms;android-36`, `ndk;28.2.13676358`, `cmake;3.31.6`
2. Set `ANDROID_HOME` environment variable
3. Build the module:
   ```bash
   cd /path/to/example && ./gradlew assembleDebug
   ```
4. Output: `app/build/outputs/apk/debug/app-debug.apk`

## Credits

This project incorporates MIT-licensed code and format references from:
- **[DCCM — Dead Cells Core Modding](https://github.com/dead-cells-core-modding/core)** — PAK / Atlas / CDB format implementation
- **[alivecells](https://github.com/N3rdL0rd/alivecells)** — PAK / Atlas format implementation and tooling

See [NOTICE](NOTICE) for details.

## License

This project is licensed under the Apache License 2.0 — see [LICENSE](LICENSE).

Third-party licenses: [LICENSE.DCCM](LICENSE.DCCM), [LICENSE.alivecells](LICENSE.alivecells).
