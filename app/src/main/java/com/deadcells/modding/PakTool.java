package com.deadcells.modding;

public class PakTool {
    static {
        System.loadLibrary("asset_replacer");
    }

    public static native boolean unpack(String pakPath, String outputDir);
    public static native boolean pack(String inputDir, String outputPak, String stamp);
    public static native boolean merge(String outputPak, String stamp, String[] inputPaks);
    public static native boolean atlasUnpack(String atlasPath, String outputDir);
    public static native boolean atlasPack(String inputDir, String outputAtlas, String outputPng);
}
