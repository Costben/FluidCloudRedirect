package cc.csxcl.fluidredirect

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * LibXposed API 102 模块入口。
 */
class MainModule : XposedModule() {

    companion object {
        const val TAG = "FluidCloudRedirect"
        const val METIS_PACKAGE = "com.oplus.metis"
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        ModulePrefs.initHost(this)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != METIS_PACKAGE) return
        ModulePrefs.initHost(this)
        log(Log.INFO, TAG, "Initializing hooks for ${param.packageName}")
        MetisRedirectHook.init(this, param.classLoader)
    }
}
