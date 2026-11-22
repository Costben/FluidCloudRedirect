package cc.csxcl.fluidredirect

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 流体云第三方客户端跳转与图标替换 Hook (LibXposed API 102)
 * 目标进程：com.oplus.metis（智能服务 / 流体云卡片）
 */
object MetisRedirectHook {
    private const val TAG = "[FluidCloudRedirect:Metis]"

    private data class RedirectRule(
        val id: String,
        val officialPackage: String,
        val replacementPackage: String,
        val isEnabled: () -> Boolean,
        val normalizeUri: ((String) -> String)? = null,
    )

    private val RULES = arrayOf(
        RedirectRule(
            id = "coolapk",
            officialPackage = "com.coolapk.market",
            replacementPackage = "app.awavenue.c001apk.coui",
            isEnabled = { ModulePrefs.isMetisCoolapkRedirectEnabled() },
            normalizeUri = ::normalizeCoolapkUri,
        ),
        RedirectRule(
            id = "weibo",
            officialPackage = "com.sina.weibo",
            replacementPackage = "com.cobo.app",
            isEnabled = { ModulePrefs.isMetisWeiboRedirectEnabled() },
            normalizeUri = null,
        ),
        RedirectRule(
            id = "bilibili",
            officialPackage = "tv.danmaku.bili",
            replacementPackage = "erl.bilico",
            isEnabled = { ModulePrefs.isMetisBilibiliRedirectEnabled() },
            normalizeUri = null,
        ),
    )

    private const val TEXT_INTENT_PACKAGE_UTIL = "com.oplus.textintent.common.utils.PackageUtil"
    private const val OINTENT_PACKAGE_UTIL = "com.oplus.ointent.api.utils.PackageUtil"
    private const val ZOOM_OPEN_HANDLER = "com.oplus.textintent.common.utils.ZoomOpenHandler"
    private const val SEEDLING_CLASS = "com.oplus.textintent.pantanal.Seedling"
    private const val FILE_SHARE_HELPER_CLASS = "com.oplus.pantanal.seedling.file.FileShareHelper"
    private const val ICON_FILE_PREFIX = "fc_redirect_icon_"
    private const val ICON_RENDER_VERSION = 2
    private const val ICON_SIZE_PX = 256

    private val TARGET_CLASSES = arrayOf(
        TEXT_INTENT_PACKAGE_UTIL,
        OINTENT_PACKAGE_UTIL,
    )

    private val LAUNCH_CLASSES = arrayOf(
        "android.app.ContextImpl",
        "android.content.ContextWrapper",
    )

    fun init(module: XposedModule, classLoader: ClassLoader) {
        try {
            hookInstalledChecks(module, classLoader)
            hookGetLaunchIntent(module, classLoader)
            hookSeedlingShow(module, classLoader)
            hookLaunches(module, classLoader)
            hookZoomLaunches(module, classLoader)
        } catch (t: Throwable) {
            module.log(Log.ERROR, MainModule.TAG, "$TAG Init error: ${t.message}", t)
        }
    }

    private fun findActiveRule(context: Context?, officialPkg: String?): RedirectRule? {
        if (context == null || officialPkg.isNullOrEmpty()) return null
        val rule = RULES.firstOrNull { it.officialPackage == officialPkg } ?: return null
        if (!rule.isEnabled()) return null
        if (!replacementInstalled(context, rule.replacementPackage)) return null
        return rule
    }

    private fun hookInstalledChecks(module: XposedModule, classLoader: ClassLoader) {
        var totalHooked = 0
        for (className in TARGET_CLASSES) {
            val packageUtilClass = XReflect.findClass(className, classLoader) ?: continue
            for (method in packageUtilClass.declaredMethods) {
                if (method.name != "isInstalled") continue
                val params = method.parameterTypes
                if (params.size == 2 &&
                    Context::class.java.isAssignableFrom(params[0]) &&
                    params[1] == String::class.java
                ) {
                    runCatching {
                        module.hook(method).intercept { chain ->
                            val context = chain.args.getOrNull(0) as? Context
                            val queried = chain.args.getOrNull(1) as? String
                            val rule = findActiveRule(context, queried)
                            if (rule != null) {
                                module.log(
                                    Log.DEBUG,
                                    MainModule.TAG,
                                    "$TAG spoof isInstalled($queried) -> true (${rule.id})"
                                )
                                return@intercept true
                            }
                            chain.proceed()
                        }
                        totalHooked++
                    }
                }
            }
        }

        if (totalHooked > 0) {
            module.log(Log.INFO, MainModule.TAG, "$TAG Applied $totalHooked hooks on PackageUtil.isInstalled")
        } else {
            module.log(Log.WARN, MainModule.TAG, "$TAG no isInstalled method hooked")
        }
    }

    /** 云端下发 isHomePage=1 时 DeepLinkData 会调用 getLaunchIntent(context, pkg)。 */
    private fun hookGetLaunchIntent(module: XposedModule, classLoader: ClassLoader) {
        val packageUtilClass = XReflect.findClass(TEXT_INTENT_PACKAGE_UTIL, classLoader) ?: return
        var hooked = 0
        for (method in packageUtilClass.declaredMethods) {
            if (method.name != "getLaunchIntent") continue
            val params = method.parameterTypes
            if (params.size == 2 &&
                Context::class.java.isAssignableFrom(params[0]) &&
                params[1] == String::class.java
            ) {
                runCatching {
                    module.hook(method).intercept { chain ->
                        val context = chain.args.getOrNull(0) as? Context
                        val pkg = chain.args.getOrNull(1) as? String
                        val rule = findActiveRule(context, pkg)
                        if (rule != null) {
                            module.log(
                                Log.INFO,
                                MainModule.TAG,
                                "$TAG retarget getLaunchIntent: $pkg -> ${rule.replacementPackage}"
                            )
                            return@intercept chain.proceed(arrayOf<Any?>(context, rule.replacementPackage))
                        }
                        chain.proceed()
                    }
                    hooked++
                }
            }
        }
        if (hooked > 0) {
            module.log(Log.INFO, MainModule.TAG, "$TAG Applied $hooked hooks on PackageUtil.getLaunchIntent")
        }
    }

    private fun hookLaunches(module: XposedModule, classLoader: ClassLoader) {
        var totalHooked = 0
        for (className in LAUNCH_CLASSES) {
            val launchClass = XReflect.findClass(className, classLoader) ?: continue
            for (method in launchClass.declaredMethods) {
                if (method.name != "startActivity") continue
                val params = method.parameterTypes
                val isLaunch = params.isNotEmpty() &&
                    params[0] == Intent::class.java &&
                    (params.size == 1 || (params.size == 2 && params[1] == Bundle::class.java))
                if (!isLaunch) continue
                runCatching {
                    module.hook(method).intercept { chain ->
                        retargetLaunch(chain.args.getOrNull(0) as? Intent, chain.thisObject as? Context, module)
                        chain.proceed()
                    }
                    totalHooked++
                }
            }
        }

        if (totalHooked > 0) {
            module.log(Log.INFO, MainModule.TAG, "$TAG Applied $totalHooked hooks on startActivity")
        } else {
            module.log(Log.WARN, MainModule.TAG, "$TAG no startActivity method hooked")
        }
    }

    /** 小窗模式拉起路径不经由 Context.startActivity，而是直接调用 OplusZoomWindowManager。 */
    private fun hookZoomLaunches(module: XposedModule, classLoader: ClassLoader) {
        val zoomClass = XReflect.findClass(ZOOM_OPEN_HANDLER, classLoader) ?: return
        var hooked = 0
        for (method in zoomClass.declaredMethods) {
            if (method.name != "startByZoom") continue
            val params = method.parameterTypes
            if (params.size == 4 &&
                Context::class.java.isAssignableFrom(params[0]) &&
                params[1] == Intent::class.java &&
                params[3] == String::class.java
            ) {
                runCatching {
                    module.hook(method).intercept { chain ->
                        val context = chain.args.getOrNull(0) as? Context
                        val intent = chain.args.getOrNull(1) as? Intent
                        val bundle = chain.args.getOrNull(2)
                        val pkg = chain.args.getOrNull(3) as? String
                        val rule = retargetLaunch(intent, context, module)
                            ?: findActiveRule(context, pkg)
                        if (rule != null && pkg == rule.officialPackage) {
                            return@intercept chain.proceed(
                                arrayOf<Any?>(context, intent, bundle, rule.replacementPackage)
                            )
                        }
                        chain.proceed()
                    }
                    hooked++
                }
            }
        }
        if (hooked > 0) {
            module.log(Log.INFO, MainModule.TAG, "$TAG Applied $hooked hooks on ZoomOpenHandler.startByZoom")
        }
    }

    /** 把指向官方包的 Intent 改写为自定义客户端，必要时清理 Component 并归一 URI。 */
    private fun retargetLaunch(intent: Intent?, context: Context?, module: XposedModule): RedirectRule? {
        if (intent == null || context == null) return null
        val targetPackage = intent.`package` ?: intent.component?.packageName
        val rule = findActiveRule(context, targetPackage) ?: return null

        val original = intent.data?.toString()
        val normalized = rule.normalizeUri?.let { fn -> original?.let(fn) }
        if (normalized != null && normalized != original) {
            runCatching { intent.data = Uri.parse(normalized) }
        }
        if (intent.component?.packageName == rule.officialPackage) {
            intent.component = null
        }
        intent.setPackage(rule.replacementPackage)
        module.log(
            Log.INFO,
            MainModule.TAG,
            "$TAG retarget launch (${rule.id}): $original -> ${normalized ?: original} (${rule.replacementPackage})"
        )
        return rule
    }

    /**
     * c001apk 把 `coolmarket://` 后面的整段当路径来匹配：`coolmarket://feed/123` → `/feed/123`。
     * 官方 app 的深链是 `coolmarket://www.coolapk.com/feed/123`，域名会被它当成路径第一段，
     * 所以这里把域名丢掉；`com.coolapk.market` 这种自带前缀的写法原样保留。
     */
    private fun normalizeCoolapkUri(raw: String): String {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
        if (!uri.scheme.equals("coolmarket", ignoreCase = true)) return raw
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        val query = uri.query?.let { "?$it" }.orEmpty()
        return when {
            host == "com.coolapk.market" -> "coolmarket://com.coolapk.market$path$query"
            host.contains('.') -> {
                val trimmed = path.trimStart('/')
                if (trimmed.isEmpty()) raw else "coolmarket://$trimmed$query"
            }
            else -> raw
        }
    }

    private fun replacementInstalled(context: Context?, packageName: String): Boolean {
        if (context == null) return false
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    /** 流体云卡片与胶囊发送前把云端图标同步替换为第三方客户端图标。 */
    private fun hookSeedlingShow(module: XposedModule, classLoader: ClassLoader) {
        val seedlingClass = XReflect.findClass(SEEDLING_CLASS, classLoader) ?: return
        val showMethod = XReflect.findMethod(seedlingClass, "show") ?: return
        runCatching {
            module.hook(showMethod).intercept { chain ->
                runCatching {
                    replaceSeedlingIcon(module, classLoader, chain.thisObject)
                }.onFailure { t ->
                    module.log(Log.WARN, MainModule.TAG, "$TAG Seedling.show icon hook error: ${t.message}", t)
                }
                chain.proceed()
            }
            module.log(Log.INFO, MainModule.TAG, "$TAG Applied hook on Seedling.show")
        }
    }

    private fun replaceSeedlingIcon(
        module: XposedModule,
        classLoader: ClassLoader,
        seedling: Any?,
    ) {
        if (seedling == null) return
        val context = XReflect.getObjectField(seedling, "context") as? Context ?: return
        val cardInfo = XReflect.getObjectField(seedling, "cardInfo") ?: return
        val data = XReflect.getObjectField(seedling, "data") ?: return
        val rawText = XReflect.getObjectField(data, "text") as? String
        if (rawText.isNullOrBlank()) return

        val rule = matchRuleFromActionJson(context, rawText) ?: return
        val iconUri = resolveReplacementIconUri(context, classLoader, rule) ?: return
        XReflect.callMethod(cardInfo, "setCardIcon", iconUri)
        XReflect.callMethod(cardInfo, "setCapsIcon", iconUri)
        module.log(Log.INFO, MainModule.TAG, "$TAG retarget icon (${rule.id}): $iconUri")
    }

    private fun matchRuleFromActionJson(context: Context, rawText: String): RedirectRule? {
        val appPackages = runCatching {
            JSONObject(rawText).optJSONObject("platform")?.optJSONArray("appPackage")
        }.getOrNull() ?: return null
        for (i in 0 until appPackages.length()) {
            val pkg = appPackages.optString(i)
            val rule = findActiveRule(context, pkg)
            if (rule != null) return rule
        }
        return null
    }

    private fun resolveReplacementIconUri(
        context: Context,
        classLoader: ClassLoader,
        rule: RedirectRule,
    ): String? {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(rule.replacementPackage, 0)
        }.getOrNull() ?: return null

        val helperClass = XReflect.findClass(FILE_SHARE_HELPER_CLASS, classLoader) ?: return null
        val helper = XReflect.getStaticObjectField(helperClass, "INSTANCE") ?: return null
        val fileName = "$ICON_FILE_PREFIX${rule.id}_v${ICON_RENDER_VERSION}_${pkgInfo.lastUpdateTime}.png"
        val shareFile = XReflect.callMethod(helper, "getDefaultShareFileByName", fileName, context) as? File
            ?: return null

        if (!shareFile.exists() || shareFile.length() == 0L) {
            shareFile.parentFile?.listFiles { _, name ->
                name.startsWith("$ICON_FILE_PREFIX${rule.id}_") && name != fileName && name.endsWith(".png")
            }?.forEach { runCatching { it.delete() } }

            val drawable = runCatching {
                context.packageManager.getApplicationIcon(rule.replacementPackage)
            }.getOrNull() ?: return null
            if (!writeIconPng(drawable, shareFile)) return null
        }

        val uri = XReflect.callMethod(helper, "getDefaultFileUri", fileName, context) as? Uri ?: return null
        val uriStr = uri.toString()
        return if (uriStr.isNotEmpty() && uri != Uri.EMPTY) uriStr else null
    }

    private fun writeIconPng(drawable: Drawable, targetFile: File): Boolean {
        val parent = targetFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false

        val bitmap = renderFullBleedIcon(drawable, ICON_SIZE_PX)
        val tmpFile = File(parent, "${targetFile.name}.tmp")
        return try {
            FileOutputStream(tmpFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    tmpFile.delete()
                    return false
                }
            }
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.delete()
                return false
            }
            true
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderFullBleedIcon(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            val inset = size / 4
            drawable.background?.let { bg ->
                bg.setBounds(-inset, -inset, size + inset, size + inset)
                bg.draw(canvas)
            }
            drawable.foreground?.let { fg ->
                drawNormalizedForeground(fg, canvas, size)
            }
            return bitmap
        }
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun drawNormalizedForeground(fg: Drawable, targetCanvas: Canvas, size: Int) {
        val fgBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            fg.setBounds(0, 0, size, size)
            fg.draw(Canvas(fgBitmap))

            val pixels = IntArray(size * size)
            fgBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            var minX = size
            var minY = size
            var maxX = -1
            var maxY = -1
            for (y in 0 until size) {
                val rowOffset = y * size
                for (x in 0 until size) {
                    if ((pixels[rowOffset + x] ushr 24) > 20) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < minX || maxY < minY) return

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (bw >= size * 92 / 100 && bh >= size * 92 / 100) {
                targetCanvas.drawBitmap(fgBitmap, 0f, 0f, paint)
                return
            }

            val targetRatio = if (bw > bh * 1.4f) 0.66f else 0.58f
            val scale = (size * targetRatio) / maxOf(bw, bh)
            val dstW = bw * scale
            val dstH = bh * scale
            val dstLeft = (size - dstW) / 2f
            val dstTop = (size - dstH) / 2f
            targetCanvas.drawBitmap(
                fgBitmap,
                Rect(minX, minY, maxX + 1, maxY + 1),
                RectF(dstLeft, dstTop, dstLeft + dstW, dstTop + dstH),
                paint,
            )
        } finally {
            fgBitmap.recycle()
        }
    }
}
