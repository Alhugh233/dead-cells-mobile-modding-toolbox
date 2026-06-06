package com.deadcells.modding

import android.util.Log
import androidx.annotation.NonNull
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.File

class ModuleMain : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: ${param.processName}")
        log(Log.INFO, TAG, "framework: %s (%s) API %d".format(
            frameworkName, frameworkVersionCode, apiVersion
        ))
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        log(Log.INFO, TAG, "onPackageLoaded: ${param.packageName}")
    }

    override fun onPackageReady(@NonNull param: PackageReadyParam) {
        if (param.isFirstPackage) {
            try {
                System.loadLibrary("asset_replacer")
                log(Log.INFO, TAG, "Native lib loaded for package: ${param.packageName}")
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to load native lib", t)
            }
            try {
                AssetReplacer.nativeInit(param.packageName)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to init AssetReplacer", t)
            }
        }

        if (param.packageName == "com.playdigious.deadcells.mobile") {
            installInternationalModHooks(param)
        }
    }

    private fun installInternationalModHooks(param: PackageReadyParam) {
        val cl = param.classLoader
        val pkg = param.packageName
        val modPackName = "AssetPackMod"

        // Detect PAD version from existing pack directory
        val padBase = File("/data/data/$pkg/files/assetpacks")
        var padVersion = "1"
        val refPack = File(padBase, "AssetPackDelivery")
        if (refPack.isDirectory) {
            refPack.listFiles()?.firstOrNull { it.isDirectory }?.let { verDir ->
                verDir.listFiles()?.firstOrNull { it.isDirectory }?.let { subDir ->
                    padVersion = subDir.name
                } ?: run { padVersion = verDir.name }
            }
        }
        val padDir = "/data/data/$pkg/files/assetpacks/$modPackName/$padVersion/$padVersion/assets"

        val knownPaks = setOf("res.pak", "res1.pak", "res2.pak", "res3.pak", "res4.pak")

        // Create PAD directory, copy only NEW .pak files (not in known list)
        try {
            val modDir = File("/data/data/$pkg/files/mod")
            val padDirFile = File(padDir)
            padDirFile.mkdirs()

            if (modDir.isDirectory) {
                val files = modDir.listFiles()
                log(Log.INFO, TAG, "Mod dir has ${files?.size ?: 0} files")
                files?.filter {
                    it.isFile && it.name.endsWith(".pak") && it.name !in knownPaks
                }?.forEach { f ->
                    val dest = File(padDirFile, f.name)
                    if (!dest.exists() || dest.lastModified() < f.lastModified()) {
                        f.copyTo(dest, overwrite = true)
                        log(Log.INFO, TAG, "Copied new PAK to PAD: ${f.name}")
                    }
                }
            } else {
                log(Log.INFO, TAG, "Mod dir does not exist: $modDir")
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to setup PAD directory", t)
        }

        // ── 1. Hook Assets.getAssetPackLocation to return pad dir ──
        try {
            val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
            val getLocation = assetsClass.getMethod("getAssetPackLocation", String::class.java)

            hook(getLocation).intercept { chain ->
                val packName = chain.getArg(0) as? String
                if (packName == modPackName) padDir else chain.proceed()
            }
            log(Log.INFO, TAG, "getAssetPackLocation hook installed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook getAssetPackLocation", t)
        }

        // ── 2. Inject mod pack into s_fastFollowPacks ─────────────
        try {
            val loadingClass = cl.loadClass("com.playdigious.deadcells.mobile.DeadCellsLoading")
            val initAssetsMethod = loadingClass.getMethod("initAssets", android.app.Activity::class.java)

            hook(initAssetsMethod).intercept { chain ->
                chain.proceed()
                val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
                val fastField = assetsClass.getDeclaredField("s_fastFollowPacks")
                fastField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val oldList = fastField.get(null) as? List<String> ?: return@intercept null
                if (oldList.contains(modPackName)) return@intercept null
                val newList = ArrayList(oldList)
                newList.add(modPackName)
                fastField.set(null, newList)
                log(Log.INFO, TAG, "Added AssetPackMod to fastFollowPacks")
                null
            }
            log(Log.INFO, TAG, "initAssets hook installed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook initAssets", t)
        }

        // ── 3. Hook getAssetPackState — report pack as completed ──
        try {
            val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
            val getState = assetsClass.getDeclaredMethod(
                "getAssetPackState",
                String::class.java,
                cl.loadClass("com.playdigious.hlmobile.AssetPackStateReceived")
            )
            hook(getState).intercept { chain ->
                val packName = chain.getArg(0) as? String
                if (packName == modPackName) {
                    val callback = chain.getArg(1) ?: return@intercept null
                    val onSuccess = callback.javaClass.getMethod("onSuccess", Any::class.java)
                    val stateClass = cl.loadClass(
                        "com.google.android.play.core.assetpacks.AssetPackState"
                    )
                    val ctor = stateClass.declaredConstructors.firstOrNull {
                        it.parameterTypes.isEmpty()
                    } ?: return@intercept null
                    ctor.isAccessible = true
                    val state = ctor.newInstance()
                    stateClass.declaredFields.firstOrNull { it.name == "status_" || it.name == "a" }
                        ?.apply { isAccessible = true; setInt(state, 4) }
                    onSuccess.invoke(callback, state)
                } else {
                    chain.proceed()
                }
                null
            }
            log(Log.INFO, TAG, "getAssetPackState hook installed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook getAssetPackState", t)
        }
    }

    companion object {
        const val TAG = "ModuleMain"
    }
}
