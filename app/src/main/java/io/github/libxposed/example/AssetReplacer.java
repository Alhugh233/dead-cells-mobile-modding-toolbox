package io.github.libxposed.example;

public class AssetReplacer {
    static {
        try { System.loadLibrary("asset_replacer"); } catch (Throwable ignored) {}
    }

    public static native void nativeInit(String packageName);
}
