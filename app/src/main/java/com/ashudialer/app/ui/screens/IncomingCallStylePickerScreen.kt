package com.ashudialer.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Human-readable name for a stored incomingCallStyle value. Used here and as SettingsScreen's row
 * subtitle, so both always agree. An id saved by an older build ("aurora", "hyper" ...) is first
 * mapped to a current one, so this never shows a name for a style that no longer exists.
 */
fun incomingCallStyleDisplayName(styleId: String): String = when (IncomingCallStyles.normalize(styleId)) {
    IncomingCallStyles.CENTER -> "Centered"
    IncomingCallStyles.SWIPE -> "Swipe"
    else -> "Clean"
}

private data class IncomingCallStyleOption(
    val id: String,
    val name: String,
    val description: String
)

private val IncomingCallStyleOptions = listOf(
    IncomingCallStyleOption(IncomingCallStyles.CLEAN, "Clean", "White screen. Photo and name in the middle, Decline and Accept at the bottom"),
    IncomingCallStyleOption(IncomingCallStyles.CENTER, "Centered", "Big name on top, photo in the middle, a Reply with message button"),
    IncomingCallStyleOption(IncomingCallStyles.SWIPE, "Swipe", "Same calm look. Drag the green or red button up to answer or decline")
)

// Made-up caller shown in every preview, so no real person's name or number is ever displayed.
private const val SAMPLE_NAME = "Aarav Mehta"
private const val SAMPLE_NUMBER = "+91 98765 43210"

/**
 * Picker for the full-screen incoming-call look.
 *
 * The big preview at the top is the REAL screen (the same composable an incoming call uses) drawn
 * inside a phone frame at phone size and scaled down, with a made-up caller. Tap a card below it
 * to switch style and the preview updates at once. On a dark theme two more controls appear: the
 * glass toggle, and a light/dark preview switch so the person can see both looks without waiting
 * for a real call.
 */
@Composable
fun IncomingCallStylePickerScreen(
    currentStyleId: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    glassEnabled: Boolean = true,
    onGlassChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val current = IncomingCallStyles.normalize(currentStyleId)
    // Reset to the top whenever the selected style changes - the preview's
    // own height can shift slightly between styles (Swipe's drag handle
    // sits lower than Clean's Decline/Accept row, for instance), and
    // without a reset the scroll position from before the tap could land
    // mid-content on the new layout, which is what made the preview look
    // "cut off at the top" (avatar half-hidden, heading text clipped) even
    // though nothing was actually broken - it just hadn't scrolled back up.
    val scrollState = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(current) { scrollState.scrollTo(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Incoming call screen", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "This is exactly how an incoming call will look",
                fontSize = 13.sp,
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(14.dp))

            // Which look to preview. A light theme only ever has the light look, so the switch is
            // shown only where there is a choice (dark theme).
            var previewDark by remember { mutableStateOf(palette.isDark) }
            PhoneFramePreview(styleId = current, dark = previewDark && palette.isDark, glass = glassEnabled)

            if (palette.isDark) {
                Spacer(Modifier.height(12.dp))
                PreviewModeSwitch(
                    dark = previewDark,
                    palette = palette,
                    onChange = { previewDark = it }
                )
            }

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IncomingCallStyleOptions.forEach { option ->
                    StyleOptionRow(
                        option = option,
                        palette = palette,
                        selected = option.id == current,
                        onClick = { onSelect(option.id) }
                    )
                }
            }

            if (palette.isDark) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().glassCard(palette, 18.dp)) {
                    ToggleRow(
                        title = "Glass look on dark theme",
                        subtitle = "Frosted dark panels. Turn off to keep the white screen even on a dark theme",
                        checked = glassEnabled,
                        palette = palette,
                        onToggle = onGlassChange
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A phone-shaped frame around the real incoming-call composable.
 *
 * The content is laid out at 360 x 740 dp (a normal phone) and scaled down with graphicsLayer, so
 * the text and buttons keep their true proportions instead of being redrawn as a mock. The frame is
 * a rounded dark bezel with a small speaker slot at the top, which is what makes it read as "a
 * phone" and not just a card.
 */
@Composable
private fun PhoneFramePreview(styleId: String, dark: Boolean, glass: Boolean) {
    val bezel = 7.dp
    val screenW = 214.dp
    val screenH = 214.dp * 740f / 360f

    Box(
        modifier = Modifier
            .size(screenW + bezel * 2, screenH + bezel * 2)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF111318))
            .padding(bezel),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .size(screenW, screenH)
                .clip(RoundedCornerShape(24.dp))
        ) {
            // Render directly at the preview viewport's exact 360:740 aspect ratio.
            // The previous requiredSize()+graphicsLayer approach could leave the transformed
            // content clipped on some device densities, which made the preview look cut in half.
            IncomingCallScreen(
                callerName = SAMPLE_NAME,
                callerNumber = SAMPLE_NUMBER,
                isSavedContact = true,
                style = styleId,
                onAccept = {},
                onDecline = {},
                onQuickMessage = {},
                modifier = Modifier.fillMaxSize(),
                isPreview = true,
                glass = glass,
                forceDark = dark
            )
        }
        // Speaker slot
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(34.dp, 3.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2E38))
        )
    }
}

/** Light / Dark preview switch (only shown on dark themes). */
@Composable
private fun PreviewModeSwitch(dark: Boolean, palette: DialerPalette, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(50))
            .padding(3.dp)
    ) {
        PreviewChip("Dark glass", dark, palette) { onChange(true) }
        PreviewChip("Light", !dark, palette) { onChange(false) }
    }
}

@Composable
private fun PreviewChip(label: String, selected: Boolean, palette: DialerPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else palette.textSecondary
        )
    }
}

@Composable
private fun StyleOptionRow(
    option: IncomingCallStyleOption,
    palette: DialerPalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "style-row-scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.accent.copy(alpha = .10f) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.accent.copy(alpha = .7f) else palette.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                option.name,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                option.description,
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3
            )
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
