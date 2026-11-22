package cc.csxcl.fluidredirect

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.TimeUnit

data class RestartOutcome(val ok: Boolean, val message: String)

/**
 * LibXposed API 102 框架连接与作用域管理中心。
 */
object ScopeManager {

    val REQUIRED_SCOPES = listOf(MainModule.METIS_PACKAGE)

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var xposedService: XposedService? = null

    var isConnected by mutableStateOf(false)
        private set

    var frameworkName by mutableStateOf("")
        private set

    var frameworkVersion by mutableStateOf("")
        private set

    var apiVersion by mutableStateOf(0)
        private set

    var grantedScopes by mutableStateOf<List<String>>(emptyList())
        private set

    var isPrefsReady by mutableStateOf(false)
        private set

    var prefsVersion by mutableStateOf(0)
        private set

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    xposedService = service
                    val name = runCatching { service.frameworkName }.getOrDefault("LSPosed")
                    val version = runCatching { service.frameworkVersion }.getOrDefault("")
                    val api = runCatching { service.apiVersion }.getOrDefault(102)
                    val scopes = runCatching { service.scope }.getOrDefault(emptyList())
                    val prefsReady = ModulePrefs.attachService(service)

                    mainHandler.post {
                        isConnected = true
                        frameworkName = name
                        frameworkVersion = version
                        apiVersion = api
                        grantedScopes = scopes
                        isPrefsReady = prefsReady
                        prefsVersion++
                    }
                }

                override fun onServiceDied(service: XposedService) {
                    if (xposedService == service) {
                        xposedService = null
                    }
                    ModulePrefs.attachService(null)
                    mainHandler.post {
                        isConnected = false
                        isPrefsReady = false
                        prefsVersion++
                    }
                }
            })
        }
    }

    fun requestScope(onResult: (success: Boolean, message: String) -> Unit) {
        val service = xposedService
        if (service == null) {
            onResult(false, "未检测到 LSPosed 服务，请先在 LSPosed 管理器中启用模块")
            return
        }

        try {
            service.requestScope(REQUIRED_SCOPES, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approvedScope: List<String>) {
                    mainHandler.post {
                        grantedScopes = approvedScope
                        onResult(true, "作用域授权已通过 (${approvedScope.joinToString()})")
                    }
                }

                override fun onScopeRequestFailed(message: String) {
                    mainHandler.post {
                        onResult(false, "作用域申请未通过: $message")
                    }
                }
            })
        } catch (t: Throwable) {
            onResult(false, "请求作用域发生异常: ${t.message}")
        }
    }

    /** 结束 com.oplus.metis 进程，让系统重新拉起并加载最新配置与代码。 */
    fun restartMetisScope(): RestartOutcome {
        val script = """
            total=0
            for pid in ${'$'}(pgrep -f "^com\\.oplus\\.metis(:.*)?${'$'}"); do
              kill -9 "${'$'}pid" 2>/dev/null && total=${'$'}((total + 1))
            done
            echo "killed=${'$'}total"
        """.trimIndent()
        val noRootHint = "没拿到 root 权限：请在 KernelSU / Magisk 里给本模块授权超级用户后重试"
        return try {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            val output = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("").trim()
            val denied = output.contains("permission denied", true) ||
                output.contains("not allowed", true) ||
                output.contains("not granted", true)
            when {
                !finished -> {
                    process.destroyForcibly()
                    RestartOutcome(false, "执行超时：root 授权弹窗还没处理完")
                }
                process.exitValue() != 0 || denied -> RestartOutcome(false, noRootHint)
                else -> {
                    val killed = Regex("killed=(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (killed == 0) {
                        RestartOutcome(true, "智能服务当前未运行，下次触发时将自动加载新配置")
                    } else {
                        RestartOutcome(true, "已重启智能服务（结束 $killed 个进程）")
                    }
                }
            }
        } catch (t: Throwable) {
            RestartOutcome(false, "$noRootHint（${t.message}）")
        }
    }
}
