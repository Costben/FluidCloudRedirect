package cc.csxcl.fluidredirect

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Surface
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TargetRuleUi(
    val key: String,
    val title: String,
    val officialLabel: String,
    val officialPkg: String,
    val targetLabel: String,
    val targetPkg: String,
)

private val REDIRECT_RULES_UI = listOf(
    TargetRuleUi(
        key = ModulePrefs.KEY_METIS_COOLAPK_REDIRECT,
        title = "酷安链接跳转 c001apk",
        officialLabel = "酷安",
        officialPkg = "com.coolapk.market",
        targetLabel = "c001apk",
        targetPkg = "app.awavenue.c001apk.coui",
    ),
    TargetRuleUi(
        key = ModulePrefs.KEY_METIS_WEIBO_REDIRECT,
        title = "微博链接跳转 Cobo",
        officialLabel = "微博",
        officialPkg = "com.sina.weibo",
        targetLabel = "Cobo",
        targetPkg = "com.cobo.app",
    ),
    TargetRuleUi(
        key = ModulePrefs.KEY_METIS_BILIBILI_REDIRECT,
        title = "哔哩哔哩链接跳转 BiliCO",
        officialLabel = "哔哩哔哩",
        officialPkg = "tv.danmaku.bili",
        targetLabel = "BiliCO",
        targetPkg = "erl.bilico",
    ),
)

private val RefreshIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Refresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            close()
        }
    }.build()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ScopeManager.init()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            COUITheme(colors = colors) {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    val scrollBehavior = COUIScrollBehavior()
    val prefsVersion = ScopeManager.prefsVersion
    val sp = remember(prefsVersion) { ModulePrefs.getAppPrefs(context) }
    var revision by remember { mutableIntStateOf(0) }
    var showRestartDialog by remember { mutableStateOf(false) }

    Coui17Scaffold(
        topBar = {
            Coui17TopAppBar(
                title = "FluidCloudRedirect",
                scrollBehavior = scrollBehavior,
                actions = {
                    BarIconButton(RefreshIcon, "重启智能服务") { showRestartDialog = true }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = innerPadding.listPadding(top = 12.dp, bottom = 24.dp),
            ) {
                item(key = "title_framework") {
                    GroupTitle("框架与作用域 (API 102)")
                }
                item(key = "card_framework") {
                    FrameworkCard(context)
                }

                item(key = "title_rules") {
                    GroupTitle("流体云跳转规则")
                }
                item(key = "card_rules") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        REDIRECT_RULES_UI.forEachIndexed { index, rule ->
                            if (index > 0) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                            RuleSwitchRow(
                                rule = rule,
                                checked = remember(revision, prefsVersion) {
                                    ModulePrefs.readBoolean(sp, rule.key, true)
                                },
                                onCheckedChange = { checked ->
                                    ModulePrefs.setBoolean(context, rule.key, checked)
                                    revision++
                                },
                            )
                        }
                    }
                }

                item(key = "title_about") {
                    GroupTitle("关于")
                }
                item(key = "card_about") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        ArrowPreference(
                            title = "模块版本",
                            summary = "${BuildConfig.VERSION_NAME} (LibXposed API 102)",
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "FluidCloudRedirect v${BuildConfig.VERSION_NAME}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ArrowPreference(
                            title = "作用域",
                            summary = "仅需勾选「智能服务 (${MainModule.METIS_PACKAGE})」",
                            onClick = {},
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ArrowPreference(
                            title = "开关行为",
                            summary = "开启后重定向至对应第三方客户端并替换图标；关闭或未安装目标应用时恢复原版官方跳转",
                            onClick = {},
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ArrowPreference(
                            title = "开源协议",
                            summary = "GNU General Public License v3.0",
                            onClick = {},
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }

        RestartMetisDialog(
            show = showRestartDialog,
            onDismiss = { showRestartDialog = false },
        )
    }
}

@Composable
private fun RuleSwitchRow(
    rule: TargetRuleUi,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var installed by remember(rule.targetPkg) {
        mutableStateOf(cachedAppIconInfo(rule.targetPkg)?.installed)
    }

    LaunchedEffect(rule.targetPkg) {
        if (installed == null) {
            installed = withContext(Dispatchers.IO) { appIconInfo(context, rule.targetPkg).installed }
        }
    }

    val statusText = when (installed) {
        true -> "已安装 ${rule.targetPkg}"
        false -> "未安装 ${rule.targetPkg}（当前保持系统原行为）"
        null -> rule.targetPkg
    }
    val behaviorText = if (checked) {
        "开启：流体云${rule.officialLabel}链接将打开 ${rule.targetLabel}"
    } else {
        "关闭：恢复${rule.officialLabel}官方客户端跳转"
    }

    SwitchPreference(
        title = rule.title,
        summary = "$behaviorText\n$statusText",
        startAction = {
            AppIcon(
                pkg = rule.targetPkg,
                fallbackLabel = rule.targetLabel,
                size = 32.dp,
                cornerRadius = 8.dp,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun FrameworkCard(context: Context) {
    val metisGranted = MainModule.METIS_PACKAGE in ScopeManager.grantedScopes
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        ArrowPreference(
            title = "框架连接状态",
            summary = if (ScopeManager.isConnected) {
                "${ScopeManager.frameworkName} ${ScopeManager.frameworkVersion} (API ${ScopeManager.apiVersion})"
            } else {
                "未连接 / 请在 LSPosed 管理器中启用模块并勾选智能服务"
            },
            onClick = {
                val msg = if (ScopeManager.isConnected) {
                    "已与 ${ScopeManager.frameworkName} 建立通信"
                } else {
                    "未收到框架注入，请确认模块已启用"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ArrowPreference(
            title = "配置通道",
            summary = if (ScopeManager.isPrefsReady) {
                "已连接，开关变更实时同步给智能服务"
            } else {
                "未连接：开关暂存在本地，请先启用模块并授权作用域"
            },
            onClick = {},
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ArrowPreference(
            title = "向框架申请作用域",
            summary = if (metisGranted) {
                "已授权：智能服务 (${MainModule.METIS_PACKAGE})"
            } else {
                "点击向 LSPosed 申请「智能服务」作用域授权"
            },
            onClick = {
                ScopeManager.requestScope { _, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun RestartMetisDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DialogDefaults.insideMargin.width)
                .padding(
                    top = DialogDefaults.insideMargin.height,
                    bottom = DialogDefaults.insideMargin.height,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(8.dp),
                color = COUITheme.colorScheme.surfaceContainerHigh,
                contentColor = COUITheme.colorScheme.onSurface,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = RefreshIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = "重启作用域",
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                fontSize = COUITheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = DialogDefaults.titleColor(),
            )

            Text(
                text = "将结束「智能服务 (com.oplus.metis)」进程使其立即重新加载最新规则。",
                modifier = Modifier.padding(bottom = 20.dp),
                style = COUITheme.textStyles.body2,
                textAlign = TextAlign.Center,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )

            Button(
                onClick = {
                    onDismiss()
                    Toast.makeText(context, "正在重启智能服务…", Toast.LENGTH_SHORT).show()
                    Thread {
                        val outcome = ScopeManager.restartMetisScope()
                        mainHandler.post {
                            Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("重启智能服务")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }
    }
}
