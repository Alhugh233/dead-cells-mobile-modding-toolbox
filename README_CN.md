# 死亡细胞手机版模组工具箱

> [English / 英文](README.md)

基于 [libxposed/example](https://github.com/libxposed/example) 模板构建的 [LSPosed](https://github.com/LSPosed/LSPosed) 模块，提供死亡细胞手机版的多种模组功能。

> **免责声明：** 本项目通过 vibe coding（AI 辅助开发）创建。代码质量可能参差不齐。使用需自担风险。

## 功能

### 资产注入（运行时）

| 功能 | 描述 | 状态 |
|------|------|------|
| 资产替换 | 用修改版替换 APK 中已有的 `.pak` 文件 | ✅ 可用 |
| 资产注入 | 注入 APK 中不存在的新 `.pak` 文件 | ❌ 游戏会忽略注入的文件 |
| PAD 路径重定向 | 支持 Google Play Asset Delivery 路径（国际版） | ✅ 可用 |
| Native Hook | 通过 LSPlant Hook `AAssetManager_open` + `open()` 系统调用 | ✅ 可用 |

### PAK 工具箱（离线，通过模块应用界面操作）

| 功能 | 描述 | 状态 |
|------|------|------|
| PAK 解包 | 将 `.pak` 文件提取为目录树 | ✅ 可用 |
| PAK 打包 | 从目录构建 `.pak`（自动保留 stamp） | ✅ 可用 |
| PAK 合并 | 将多个 `.pak` 合并为一个（同名覆盖） | ✅ 可用 |
| Atlas 解包 | 将 `.atlas`（BATL）提取为精灵坐标列表 | ✅ 可用 |
| Atlas 打包 | 从精灵图片构建 `.atlas` + 纹理 PNG | ⚠️ 未经测试 |
| MO ↔ PO | MO/PO 转换（gettext） | ❌ 已移除 — 游戏使用非标准格式 |

### 支持的版本

| 平台 | 包名 | 状态 |
|------|------|------|
| 哔哩哔哩（中国版） | `com.bilibili.deadcells.mobile` | ✅ 可用 |
| Google Play（国际版） | `com.playdigious.deadcells.mobile` | ✅ 可用 |

> **⚠️ 国际版特别说明：** Google Play 正版包含 **pairip**（Play Integrity / 反篡改机制）。如果你使用的是国际版，必须在 LSPosed 的模块设置中为死亡细胞勾选 **"还原内联钩子"** 选项，否则 pairip 会检测到修改并导致闪退。
>
> ![](docs/pairip_setting_1.jpg)
> ![](docs/pairip_setting_2.jpg)

## 工作原理

模块使用双层 Hook 机制：

1. **Java 层** — Hook 游戏的 `DeadCells.onStart()` → `Assets.init()` 链，在 AssetManager 传入 native 前拦截
2. **Native 层** — Hook `libandroid.so` 中的 `AAssetManager_open`、`AAssetManager_openDir`、`AAssetDir_getNextFileName`、`AAsset_read`、`AAsset_seek`、`AAsset_close`、`AAsset_getLength`、`AAsset_openFileDescriptor`，以及 `libc.so` 中的 `open()`

Mod 文件存放路径：
- 中国版：`/storage/emulated/0/Android/data/com.bilibili.deadcells.mobile/mod/`
- 国际版：`/storage/emulated/0/Android/data/com.playdigious.deadcells.mobile/mod/`
  （若作用域存储阻止以上路径，则回退至 `/data/data/<pkg>/files/mod/`）

## 构建

1. 安装 Android SDK（包含 `platforms;android-36`、`build-tools;36.1.0`、`ndk;28.2.13676358`、`cmake;3.31.6`）
2. 设置 `ANDROID_HOME` 环境变量
3. 构建模块：
   ```bash
   cd /path/to/example && ./gradlew assembleDebug
   ```
4. 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 致谢

本项目包含以下 MIT 许可的开源代码和格式参考：
- **[DCCM — Dead Cells Core Modding](https://github.com/dead-cells-core-modding/core)** — PAK / Atlas / CDB 格式实现
- **[alivecells](https://github.com/N3rdL0rd/alivecells)** — PAK / Atlas 格式实现及工具

详见 [NOTICE](NOTICE)。

## 协议

本项目基于 Apache License 2.0 许可 — 详见 [LICENSE](LICENSE)。

第三方协议：[LICENSE.DCCM](LICENSE.DCCM), [LICENSE.alivecells](LICENSE.alivecells)。
