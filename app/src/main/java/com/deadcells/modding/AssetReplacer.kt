package com.deadcells.modding

object AssetReplacer {
    init {
        try {
            System.loadLibrary("asset_replacer")
        } catch (_: Throwable) {}
    }

    @JvmStatic
    external fun nativeInit(packageName: String)
}
