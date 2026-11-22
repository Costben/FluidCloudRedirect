package cc.csxcl.fluidredirect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_MAX_PX = 144

data class AppIconInfo(
    val pkg: String,
    val label: String,
    val icon: Bitmap?,
    val installed: Boolean,
)

private val appIconCache = HashMap<String, AppIconInfo>()

fun cachedAppIconInfo(pkg: String): AppIconInfo? = synchronized(appIconCache) { appIconCache[pkg] }

fun appIconInfo(context: Context, pkg: String): AppIconInfo {
    cachedAppIconInfo(pkg)?.let { return it }
    val info = runCatching {
        val pm = context.packageManager
        val applicationInfo = pm.getApplicationInfo(pkg, 0)
        AppIconInfo(
            pkg = pkg,
            label = pm.getApplicationLabel(applicationInfo).toString(),
            icon = toIconBitmap(pm.getApplicationIcon(applicationInfo)),
            installed = true,
        )
    }.getOrElse {
        AppIconInfo(pkg = pkg, label = pkg, icon = null, installed = false)
    }
    synchronized(appIconCache) { appIconCache[pkg] = info }
    return info
}

@Composable
fun AppIcon(
    pkg: String,
    fallbackLabel: String,
    size: Dp = 32.dp,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var info by remember(pkg) { mutableStateOf(cachedAppIconInfo(pkg)) }

    LaunchedEffect(pkg) {
        if (info == null) {
            info = withContext(Dispatchers.IO) { appIconInfo(context, pkg) }
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val icon = info?.icon
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
        )
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(COUITheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fallbackLabel.take(1),
            style = COUITheme.textStyles.headline2,
            fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun toIconBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_MAX_PX
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_MAX_PX
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(Canvas(bitmap))
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= ICON_MAX_PX) return bitmap
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ICON_MAX_PX / longest).coerceAtLeast(1),
        (bitmap.height * ICON_MAX_PX / longest).coerceAtLeast(1),
        true,
    )
}
