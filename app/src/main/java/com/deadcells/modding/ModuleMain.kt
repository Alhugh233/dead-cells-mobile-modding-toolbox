package com.deadcells.modding

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

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

    override fun onPackageReady(param: PackageReadyParam) {
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
    }

    companion object {
        const val TAG = "ModuleMain"
    }
}
