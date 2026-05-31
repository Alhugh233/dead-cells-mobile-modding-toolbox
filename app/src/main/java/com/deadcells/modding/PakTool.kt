package com.deadcells.modding

object PakTool {
    init {
        System.loadLibrary("asset_replacer")
    }

    external fun unpack(pakPath: String, outputDir: String): Boolean
    external fun pack(inputDir: String, outputPak: String, stamp: String?): Boolean
    external fun merge(outputPak: String, stamp: String?, vararg inputPaks: String): Boolean
    external fun atlasUnpack(atlasPath: String, outputDir: String): Boolean
    external fun atlasPack(inputDir: String, outputAtlas: String, outputPng: String): Boolean
}
