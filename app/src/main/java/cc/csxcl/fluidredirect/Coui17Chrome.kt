package cc.csxcl.fluidredirect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun Coui17Scaffold(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val pageColor = AppBarBlurOverlayDefaults.containerColor()
    val backdrop = rememberLayerBackdrop {
        drawRect(pageColor)
        drawContent()
    }
    Scaffold(
        containerColor = pageColor,
        edgeToEdge = true,
        topBar = { CompositionLocalProvider(LocalBackdrop provides backdrop) { topBar() } },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            content(innerPadding)
        }
    }
}

@Composable
fun PaddingValues.listPadding(
    top: Dp,
    bottom: Dp,
    start: Dp = 0.dp,
    end: Dp = 0.dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + start,
        top = calculateTopPadding() + top,
        end = calculateEndPadding(layoutDirection) + end,
        bottom = calculateBottomPadding() + bottom,
    )
}

@Composable
fun Coui17TopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        color = AppBarBlurOverlayDefaults.containerColor(),
        scrollBehavior = scrollBehavior,
        gradientGlass = true,
        showDivider = false,
        actions = actions,
    )
}

@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    HighlightedIconButton(
        onClick = onClick,
        modifier = Modifier.couiTopAppBarActionItem(),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
fun GroupTitle(
    text: String,
    modifier: Modifier = Modifier,
    cardInset: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = cardInset)
            .padding(bottom = 3.5.dp)
            .height(32.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(start = 16.dp),
            style = COUITheme.textStyles.headline2,
            color = COUITheme.colorScheme.onSurface,
        )
    }
}
