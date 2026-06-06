# Dead Cells Mobile Modding Toolbox

> [中文 / Chinese](README_CN.md)

A multi-purpose modding toolbox for Dead Cells Mobile (Android), built as an [LSPosed](https://github.com/LSPosed/LSPosed) module based on the [libxposed/example](https://github.com/libxposed/example) template. It doubles as a standalone PAK/Atlas toolkit that works without root.

> **Disclaimer:** This project was created via vibe coding (AI-assisted development). Code quality may vary. Use at your own risk.

## Features

### Asset Injection (Runtime — requires LSPosed)

| Feature | Description | Status |
|---------|-------------|--------|
| Asset replacement | Replace existing `.pak` files with modded versions | ✅ Working |
| Asset injection | Inject new `.pak` files that don't exist in the APK | ✅ Working (CN) / via PAD symlink (Global) |
| PAD path redirection | Support for Google Play Asset Delivery paths (global version) | ✅ Working |
| Native hook | Hooks `AAssetManager_open` + `open()` syscall via LSPlant | ✅ Working |

> If the mod version does not match the game version, you can try the **PAK merge** approach: use the built-in merge tool to merge the mod's `.pak` into one of the game's existing asset files (e.g. merge `res5.pak` into `res4.pak`). This forces the game to treat the mod content as built-in resources, bypassing version checks — but may cause unexpected issues.

### PAK Toolkit (Offline — no root required)

| Feature | Description | Status |
|---------|-------------|--------|
| PAK unpack | Extract `.pak` files to directory tree | ✅ Working |
| PAK pack | Build `.pak` from directory (auto stamp preservation) | ✅ Working |
| PAK merge | Merge multiple `.pak` into one (overwrite logic) | ✅ Working |
| Atlas unpack | Extract `.atlas` (BATL) to individual PNG sprites + coordinates | ⚠️ Experimental |
| Atlas pack | Build `.atlas` + atlas PNG from individual sprites | ⚠️ Experimental |

### Supported Versions

| Platform | Package ID | Status |
|----------|-----------|--------|
| Bilibili (China) | `com.bilibili.deadcells.mobile` | ✅ Working |
| Google Play (Global) | `com.playdigious.deadcells.mobile` | ✅ Working |

> **⚠️ Google Play version note:** The official Google Play build includes **pairip** (Play Integrity / anti-tamper). If you are using the Google Play version, you must enable **"Invalidate inline hooks"** for Dead Cells in LSPosed's module settings. Otherwise pairip will detect the modification and crash the game.
>
> **International version PAK injection:** New `.pak` files (e.g. `res5.pak`) are automatically symlinked into a fake PAD asset pack (`AssetPackMod`). The game's native PAD scanning code discovers and loads them alongside the official PAD packs. No manual setup required — just place the file in the mod directory.
>
> **⚠️ PAD version updates:** When Dead Cells receives an update on the Google Play Store, the PAD asset packs must be re-downloaded. If mods are active during the update, PAD redirection may interfere with the download process. It is recommended to **temporarily disable the module** (uncheck in LSPosed scope) before updating, then re-enable after the update completes.
>
> ![](docs/pairip_setting_1.jpg)
> ![](docs/pairip_setting_2.jpg)

## How It Works

The module uses a multi-layer hooking approach:

1. **Native asset hooks** — Hooks `AAssetManager_open`, `openDir`, `getNextFileName`, `read`, `seek`/`seek64`, `close`, `getLength`/`getLength64`, `openFileDescriptor`/`openFileDescriptor64` in `libandroid.so`, plus `open()` in `libc.so` for PAD path redirection
2. **Java PAD hooks** (international only) — Hooks `Assets.getAssetPackLocation()` / `getAssetPackState()` and `DeadCellsLoading.initAssets()` to auto-inject new `.pak` files as a fake PAD asset pack (`AssetPackMod`), bypassing Google Play PAD IPC
3. **Directory-based injection** (CN only) — `openDir` + `getNextFileName` hooks return injected entries alongside original assets, enabling the game's native `mobile_Res_initAssets` second-pass scan to discover new `.pak` files

Mod files go to:
- Bilibili: `/storage/emulated/0/Android/data/com.bilibili.deadcells.mobile/mod/`
- Google Play: `/data/data/com.playdigious.deadcells.mobile/files/mod/`
  (external storage is not accessible due to scoped storage restrictions)

> **⚠️ Rootless environments (LSPatch / forks):** While the module can theoretically be loaded without root via [LSPatch](https://github.com/LSPosed/LSPatch) or its forks, these environments differ from native LSPosed and compatibility is not guaranteed. For the Google Play version specifically, LSPatch-style patching involves modifying the APK itself, which **will** trigger pairip's anti-tamper detection and cause a crash — unless you are using a cracked/pirated copy that already has pairip removed.

## Building

**Requirements:** Android SDK `platforms;android-37.0`, `ndk;27.0.12077973`, `cmake;3.31.6`.  
Gradle 9.4.1 is auto-downloaded by the wrapper.

```bash
git clone https://github.com/Alhugh233/dead-cells-mobile-modding-toolbox
cd dead-cells-mobile-modding-toolbox
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Credits

This project incorporates code and format references from:
- **[DCCM — Dead Cells Core Modding](https://github.com/dead-cells-core-modding/core)** — PAK / Atlas / CDB format implementation (MIT)
- **[alivecells](https://github.com/N3rdL0rd/alivecells)** — PAK / Atlas format implementation and tooling (MIT)
- **[Miuix](https://github.com/compose-miuix-ui/miuix)** — Compose Multiplatform UI library (Apache 2.0)

See [NOTICE](NOTICE) for details.

## License

This project is licensed under the Apache License 2.0 — see [LICENSE](LICENSE).

Third-party licenses: [LICENSE.DCCM](LICENSE.DCCM), [LICENSE.alivecells](LICENSE.alivecells), [LICENSE.Miuix](LICENSE.Miuix).
