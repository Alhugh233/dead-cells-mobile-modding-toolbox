package com.deadcells.modding

import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AssetInjector {
    private const val TAG = "AssetInjector"

    private val MINIMAL_MANIFEST = byteArrayOf(
        0x03, 0x00, 0x08, 0x00, 0x18, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x1C, 0x00, 0x1C, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x01, 0x10, 0x00, 0x18, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, (-1).toByte(), (-1).toByte(), (-1).toByte(), (-1).toByte(),
        0x02, 0x01, 0x10, 0x00, 0x18, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x03, 0x01, 0x10, 0x00, 0x10, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    fun buildModApk(pkgName: String, modDirExternal: String, originalAm: AssetManager): String? {
        var dir = File(modDirExternal)
        if (!dir.isDirectory) {
            dir = File("/data/data/$pkgName/files/mod")
        }
        if (!dir.isDirectory) return null

        val apkPath = "/data/data/$pkgName/files/.inject.apk"
        val originalAssets = try {
            originalAm.list("") ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        val files = dir.listFiles() ?: return null

        val hasNew = files.any { f ->
            f.isFile && !f.name.startsWith(".") && f.name !in originalAssets
        }
        if (!hasNew) return null

        val apkFile = File(apkPath)
        apkFile.delete()

        ZipOutputStream(FileOutputStream(apkFile)).use { zos ->
            val crc = CRC32().apply { update(MINIMAL_MANIFEST) }.value
            ZipEntry("AndroidManifest.xml").apply {
                method = ZipEntry.STORED
                size = MINIMAL_MANIFEST.size.toLong()
                compressedSize = MINIMAL_MANIFEST.size.toLong()
                setCrc(crc)
            }.also { zos.putNextEntry(it) }
            zos.write(MINIMAL_MANIFEST)
            zos.closeEntry()

            for (f in files) {
                if (!f.isFile || f.name.startsWith(".") || f.name in originalAssets) continue
                ZipEntry("assets/${f.name}").apply {
                    method = ZipEntry.DEFLATED
                }.also { zos.putNextEntry(it) }
                FileInputStream(f).use { it.copyTo(zos, 8192) }
                zos.closeEntry()
                Log.i(TAG, "Added to inject APK: ${f.name}")
            }
        }

        Log.i(TAG, "Built inject APK: $apkPath")
        apkFile.setReadable(true, false)
        return apkPath
    }

    fun addAssetPath(am: AssetManager, apkPath: String) {
        File(apkPath).setReadable(true, false)

        try {
            val m = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            val cookie = m.invoke(am, apkPath) as Int
            Log.i(TAG, "addAssetPath($apkPath) = $cookie")
            if (cookie != 0) return
        } catch (e: Exception) {
            Log.w(TAG, "addAssetPath failed", e)
        }

        try {
            val m = AssetManager::class.java.getMethod("addAssetPathAsSharedLibrary", String::class.java)
            val cookie = m.invoke(am, apkPath) as Int
            Log.i(TAG, "addAssetPathAsSharedLibrary = $cookie")
        } catch (e: Exception) {
            Log.w(TAG, "addAssetPathAsSharedLibrary also failed", e)
        }
    }
}
