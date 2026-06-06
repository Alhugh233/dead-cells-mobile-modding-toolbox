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

        // ── 1. Hook Assets.getAssetPackLocation to return mod path ──
        try {
            val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
            val getLocation = assetsClass.getMethod("getAssetPackLocation", String::class.java)
            val modPackName = "AssetPackMod"

            // Prepare mod PAD directory structure
            val modPadDir = File("/data/data/$pkg/files/assetpacks/$modPackName/1/1/assets")
            if (!modPadDir.exists()) {
                modPadDir.mkdirs()
                log(Log.INFO, TAG, "Created mod PAD dir: ${modPadDir.absolutePath}")
            }

            hook(getLocation).intercept { chain ->
                val packName = chain.getArg<String>(0)
                if (packName == modPackName) {
                    modPadDir.absolutePath
                } else {
                    chain.proceed()
                }
            }
            log(Log.INFO, TAG, "AssetPackLocation hook installed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook getAssetPackLocation", t)
        }

        // ── 2. Inject mod pack into fastFollowAssetPacks ───────────
        try {
            val loadingClass = cl.loadClass("com.playdigious.deadcells.mobile.DeadCellsLoading")
            val initAssetsMethod = loadingClass.getMethod("initAssets", android.app.Activity::class.java)

            hook(initAssetsMethod).intercept { chain ->
                // Call original first
                chain.proceed()

                // After original Assets.init() completes, add our pack to s_fastFollowPacks
                val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
                val fastField = assetsClass.getDeclaredField("s_fastFollowPacks")
                fastField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val list = fastField.get(null) as? MutableList<String>
                if (list != null && !list.contains("AssetPackMod")) {
                    list.add("AssetPackMod")
                    log(Log.INFO, TAG, "Added AssetPackMod to fastFollowPacks")
                }
                null
            }
            log(Log.INFO, TAG, "initAssets hook installed")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook initAssets", t)
        }

        // ── 3. Hook getAssetPackState to pretend pack is completed ─
        try {
            val assetsClass = cl.loadClass("com.playdigious.hlmobile.Assets")
            val getState = assetsClass.getDeclaredMethod(
                "getAssetPackState",
                String::class.java,
                cl.loadClass("com.playdigious.hlmobile.AssetPackStateReceived")
            )
            hook(getState).intercept { chain ->
                val packName = chain.getArg<String>(0)
                val callback = chain.getArg<Any>(1)
                if (packName == "AssetPackMod" && callback != null) {
                    // Call onSuccess with a fake completed state
                    val callbackClass = callback.javaClass
                    val onSuccess = callbackClass.getMethod("onSuccess", Any::class.java)
                    // Create a fake AssetPackState
                    val stateClass = cl.loadClass("com.google.android.play.core.assetpacks.AssetPackState")
                    val constructor = stateClass.declaredConstructors.firstOrNull() ?: return@intercept
                    constructor.isAccessible = true
                    val fakeState = constructor.newInstance()
                    // Set status to 4 (completed)
                    val statusField = stateClass.getDeclaredField("status_")
                    statusField.isAccessible = true
                    statusField.setInt(fakeState, 4)
                    onSuccess.invoke(callback, fakeState)
                    log(Log.INFO, TAG, "Fake AssetPackState completed for AssetPackMod")
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
