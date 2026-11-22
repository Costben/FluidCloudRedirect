package cc.csxcl.fluidredirect

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.service.XposedService

/**
 * 模块配置与 LibXposed API 102 远程配置桥接。
 *
 * 宿主进程通过 [XposedModule.getRemotePreferences] 只读消费配置，
 * 模块 App 通过 [XposedService.getRemotePreferences] 读写同一份远程表。
 */
object ModulePrefs {

    const val PREFS_NAME = "fluid_cloud_redirect_prefs"
    private const val TAG = "FluidCloudRedirect:Prefs"
    private const val KEY_MIGRATED = "prefs_migrated_to_lsposed_dir"

    const val KEY_METIS_COOLAPK_REDIRECT = "metis_coolapk_redirect_enabled"
    const val KEY_METIS_WEIBO_REDIRECT = "metis_weibo_redirect_enabled"
    const val KEY_METIS_BILIBILI_REDIRECT = "metis_bilibili_redirect_enabled"

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    private var appRemotePrefs: SharedPreferences? = null

    @Volatile
    private var appLocalPrefs: SharedPreferences? = null

    fun initHost(module: XposedModule) {
        if (remotePrefs != null) return
        runCatching {
            remotePrefs = module.getRemotePreferences(PREFS_NAME)
            module.log(Log.INFO, TAG, "Host prefs bridged to LSPosed remote preferences")
        }.onFailure { t ->
            module.log(Log.WARN, TAG, "getRemotePreferences failed: ${t.message}")
        }
    }

    fun hostPrefs(): SharedPreferences? = remotePrefs

    /**
     * 按「键不存在就用默认值」读取布尔配置。
     * 必须先判 contains，避免 LSPosed 远程配置对缺失键直接返回 false。
     */
    fun readBoolean(prefs: SharedPreferences, key: String, default: Boolean): Boolean =
        if (prefs.contains(key)) prefs.getBoolean(key, default) else default

    fun isMetisCoolapkRedirectEnabled(): Boolean {
        val prefs = hostPrefs() ?: return true
        return runCatching { readBoolean(prefs, KEY_METIS_COOLAPK_REDIRECT, true) }.getOrDefault(true)
    }

    fun isMetisWeiboRedirectEnabled(): Boolean {
        val prefs = hostPrefs() ?: return true
        return runCatching { readBoolean(prefs, KEY_METIS_WEIBO_REDIRECT, true) }.getOrDefault(true)
    }

    fun isMetisBilibiliRedirectEnabled(): Boolean {
        val prefs = hostPrefs() ?: return true
        return runCatching { readBoolean(prefs, KEY_METIS_BILIBILI_REDIRECT, true) }.getOrDefault(true)
    }

    fun attachService(service: XposedService?): Boolean {
        val prefs = runCatching { service?.getRemotePreferences(PREFS_NAME) }.getOrNull()
        appRemotePrefs = prefs
        return prefs != null
    }

    fun getAppPrefs(context: Context): SharedPreferences {
        val remote = appRemotePrefs
        if (remote != null) {
            migrateToRemote(context, remote)
            return remote
        }
        return localPrefs(context)
    }

    private fun localPrefs(context: Context): SharedPreferences {
        appLocalPrefs?.let { return it }
        synchronized(this) {
            appLocalPrefs?.let { return it }
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            appLocalPrefs = sp
            return sp
        }
    }

    private fun migrateToRemote(context: Context, remote: SharedPreferences) {
        if (remote.getBoolean(KEY_MIGRATED, false)) return
        val editor = remote.edit()
        runCatching {
            localPrefs(context).all.forEach { (key, value) ->
                if (key == KEY_MIGRATED) return@forEach
                if (value is Boolean) editor.putBoolean(key, value)
            }
        }
        editor.putBoolean(KEY_MIGRATED, true).apply()
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        getAppPrefs(context).edit().putBoolean(key, value).apply()
    }
}
