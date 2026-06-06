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

        // ── 1. Hook Assets.getAssetPackLocation —— return PAD path ──
        // Pad version is resolved lazily in initAssets hook below
        var padDir = ""
        try {
            val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
            val getLocation = assetsClass.getMethod("getAssetPackLocation", String::class.java)
            hook(getLocation).intercept { chain ->
                val packName = chain.getArg(0) as? String
                if (packName == modPackName) {
                    if (padDir.isEmpty()) chain.proceed() else padDir
                } else chain.proceed()
            }
            log(Log.INFO, TAG, "getAssetPackLocation hook installed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook getAssetPackLocation", t)
        }

        // ── 2. Hook DeadCellsLoading.initAssets —— setup pad + inject pack ──
        try {
            val loadingClass = cl.loadClass("com.playdigious.deadcells.mobile.DeadCellsLoading")
            val initAssetsMethod = loadingClass.getMethod("initAssets", android.app.Activity::class.java)

            hook(initAssetsMethod).intercept { chain ->
                chain.proceed()
                val activity = chain.getArg(0) as? android.app.Activity ?: return@intercept null

                // Resolve pad version from activity
                if (padDir.isEmpty()) {
                    val version = try {
                        val info = activity.packageManager.getPackageInfo(pkg, 0)
                        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode
                        else @Suppress("DEPRECATION") info.versionCode.toLong()
                    } catch (_: Throwable) { null } ?: return@intercept null
                    padDir = "/data/data/$pkg/files/assetpacks/$modPackName/$version/$version/assets"
                    log(Log.INFO, TAG, "PAD padDir: $padDir")
                }

                // Create symlinks for new PAK files, remove stale ones
                val knownPaks = setOf("res.pak", "res1.pak", "res2.pak", "res3.pak", "res4.pak")
                try {
                    val modDir = File("/data/data/$pkg/files/mod")
                    val padDirFile = File(padDir)
                    padDirFile.mkdirs()

                    val modNames = modDir.listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".pak") && it.name !in knownPaks }
                        ?.map { it.name }?.toSet() ?: emptySet()

                    // Remove stale symlinks (mod file gone)
                    padDirFile.listFiles()?.forEach { dest ->
                        if (dest.name !in modNames && dest.name.endsWith(".pak")) {
                            dest.delete()
                            log(Log.INFO, TAG, "Removed stale PAD: ${dest.name}")
                        }
                    }

                    // Create new symlinks
                    modDir.listFiles()?.filter {
                        it.isFile && it.name in modNames
                    }?.forEach { f ->
                        val dest = File(padDirFile, f.name)
                        if (!dest.exists()) {
                            try {
                                java.nio.file.Files.createSymbolicLink(dest.toPath(), f.toPath())
                                log(Log.INFO, TAG, "PAD symlink: ${f.name}")
                            } catch (_: Throwable) {
                                f.copyTo(dest, overwrite = true)
                                log(Log.INFO, TAG, "PAD copy: ${f.name}")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "Failed to setup PAD dir", t)
                }

                // Inject AssetPackMod into fastFollowPacks
                val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
                val fastField = assetsClass.getDeclaredField("s_fastFollowPacks")
                fastField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val oldList = fastField.get(null) as? List<String> ?: return@intercept null
                if (!oldList.contains(modPackName)) {
                    val newList = ArrayList(oldList)
                    newList.add(modPackName)
                    fastField.set(null, newList)
                    log(Log.INFO, TAG, "Added AssetPackMod to fastFollowPacks")
                }
                null
            }
            log(Log.INFO, TAG, "initAssets hook installed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook initAssets", t)
        }

        // ── 3. Hook getAssetPackState —— report pack as completed ──
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
