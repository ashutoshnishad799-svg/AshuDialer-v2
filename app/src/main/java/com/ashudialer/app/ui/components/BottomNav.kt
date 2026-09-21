package com.ashudialer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.math.abs
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

enum class DialerTab(val label: String) {
    CONTACTS("Contacts"),
    RECENT("Recent"),
    DIALER("Dialer"),
    PROTECT("Protect"),
    MORE("More")
}


/** Height of one tab cell. The sliding pill uses the same height, so it always lines up with the tabs. */
private val NavCellHeight = 56.dp

/**
 * The bottom bar. A frosted "glass" pill slides under the selected tab, and it FOLLOWS the finger while the
 * pages are being swiped: [position] is the live pager position (0.0 = first tab, 1.0 = second, 1.5 = halfway
 * between the second and third ...). Tapping a tab slides the pill there and the page follows. Icons and labels
 * fade from grey to the accent colour as the pill reaches them, so nothing snaps.
 *
 * [position] is a lambda so the pill itself moves without recomposing the bar on every frame.
 */
@Composable
fun DialerBottomNav(
    selected: DialerTab,
    onSelect: (DialerTab) -> Unit,
    position: () -> Float = { selected.ordinal.toFloat() }
) {
    val palette = LocalDialerPalette.current
    val tabs = DialerTab.values()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .liquidGlass(palette = palette, shape = RoundedCornerShape(28.dp), tintAlpha = 0.9f)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(NavCellHeight)) {
            val cellW = maxWidth / tabs.size
            val cellWpx = with(LocalDensity.current) { cellW.toPx() }

            // The glass pill: translucent fill, a soft top-to-bottom sheen and a bright hairline edge.
            Box(
                Modifier
                    .width(cellW)
                    .height(NavCellHeight)
                    .graphicsLayer { translationX = position() * cellWpx }
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.accentSoft, palette.accentSoft.copy(alpha = 0.62f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    NavTabItem(
                        tab = tab,
                        index = index,
                        position = position,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: DialerTab,
    index: Int,
    position: () -> Float,
    palette: DialerPalette,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(if (pressed) 60 else 140),
        label = "nav-press"
    )
    // 1.0 when the pill sits exactly on this tab, 0.0 when it is a whole tab or more away.
    val near = (1f - abs(position() - index)).coerceIn(0f, 1f)
    val contentColor = lerp(palette.textSecondary, palette.accent, near)
    val isSelected = near > 0.5f

    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .pointerInput(tab) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = iconFor(tab, isSelected),
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val grow = 1f + 0.12f * near
                    scaleX = grow
                    scaleY = grow
                }
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = contentColor
        )
    }
}

private fun iconFor(tab: DialerTab, selected: Boolean): ImageVector = when (tab) {
    DialerTab.CONTACTS -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    DialerTab.RECENT -> if (selected) Icons.Filled.Phone else Icons.Outlined.Phone
    DialerTab.DIALER -> if (selected) Icons.Filled.Dialpad else Icons.Outlined.Dialpad
    DialerTab.PROTECT -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
    DialerTab.MORE -> if (selected) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz
}
