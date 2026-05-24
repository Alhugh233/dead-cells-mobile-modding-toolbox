package io.github.libxposed.example;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AssetInjector {
    private static final String TAG = "AssetInjector";

    /** Minimal valid AndroidManifest.xml (binary) for an APK with no components */
    private static final byte[] MINIMAL_MANIFEST = {
        // Magic + file size (will be patched)
        0x03, 0x00, 0x08, 0x00, 0x18, 0x00, 0x00, 0x00,
        // String chunk header
        0x01, 0x00, 0x1C, 0x00, 0x1C, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        // XmlResourceMap header + namespace start + manifest tag start/end
        0x00, 0x01, 0x10, 0x00, 0x18, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, -1, -1, -1, -1,
        0x02, 0x01, 0x10, 0x00, 0x18, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x03, 0x01, 0x10, 0x00, 0x10, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    /**
     * Build a minimal valid APK ZIP containing only new files from
     * the mod directory. Uses internal storage to avoid scoped-storage issues.
     */
    public static String buildModApk(String pkgName, String modDirExternal, AssetManager originalAm) {
        // Try external path first, fallback to internal
        File dir = new File(modDirExternal);
        if (!dir.isDirectory()) {
            dir = new File("/data/data/" + pkgName + "/files/mod");
        }
        if (!dir.isDirectory()) return null;

        // Build the inject APK in internal storage (always writable)
        String apkPath = "/data/data/" + pkgName + "/files/.inject.apk";

        String[] originalAssets;
        try {
            originalAssets = originalAm.list("");
        } catch (IOException e) {
            originalAssets = new String[0];
        }

        File[] files = dir.listFiles();
        if (files == null) return null;

        // Check if there are any new files
        boolean hasNew = false;
        for (File f : files) {
            if (!f.isFile() || f.getName().startsWith(".")) continue;
            boolean exists = false;
            for (String orig : originalAssets) {
                if (f.getName().equals(orig)) { exists = true; break; }
            }
            if (!exists) { hasNew = true; break; }
        }
        if (!hasNew) return null;

        // Always rebuild fresh
        File apkFile = new File(apkPath);
        apkFile.delete();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apkFile))) {
            // 1. AndroidManifest.xml (must be uncompressed for APK)
            ZipEntry manifestEntry = new ZipEntry("AndroidManifest.xml");
            manifestEntry.setMethod(ZipEntry.STORED);
            manifestEntry.setSize(MINIMAL_MANIFEST.length);
            manifestEntry.setCompressedSize(MINIMAL_MANIFEST.length);
            manifestEntry.setCrc(computeCrc32(MINIMAL_MANIFEST));
            zos.putNextEntry(manifestEntry);
            zos.write(MINIMAL_MANIFEST);
            zos.closeEntry();

            // 2. Add new asset files
            for (File f : files) {
                if (!f.isFile() || f.getName().startsWith(".")) continue;
                boolean exists = false;
                for (String orig : originalAssets) {
                    if (f.getName().equals(orig)) { exists = true; break; }
                }
                if (exists) continue; // skip files already in original APK

                ZipEntry entry = new ZipEntry("assets/" + f.getName());
                entry.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
                Log.i(TAG, "Added to inject APK: " + f.getName());
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to build inject APK", e);
            return null;
        }

        Log.i(TAG, "Built inject APK: " + apkPath);
        // Ensure world-readable so the asset manager can load it
        apkFile.setReadable(true, false);
        return apkPath;
    }

    private static long computeCrc32(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    public static void addAssetPath(AssetManager am, String apkPath) {
        // Ensure file is readable
        new File(apkPath).setReadable(true, false);

        // Try legacy addAssetPath first
        try {
            Method m = AssetManager.class.getMethod("addAssetPath", String.class);
            int cookie = (int) m.invoke(am, apkPath);
            Log.i(TAG, "addAssetPath(" + apkPath + ") = " + cookie);
            if (cookie != 0) return;
        } catch (Exception e) {
            Log.w(TAG, "addAssetPath failed", e);
        }

        // Fallback: try addAssetPathAsSharedLibrary (Android 10+)
        try {
            Method m = AssetManager.class.getMethod("addAssetPathAsSharedLibrary", String.class);
            int cookie = (int) m.invoke(am, apkPath);
            Log.i(TAG, "addAssetPathAsSharedLibrary = " + cookie);
        } catch (Exception e2) {
            Log.w(TAG, "addAssetPathAsSharedLibrary also failed", e2);
        }
    }
}
