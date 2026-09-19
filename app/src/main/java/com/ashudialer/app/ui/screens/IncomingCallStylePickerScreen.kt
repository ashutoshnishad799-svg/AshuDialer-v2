package com.ashudialer.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Human-readable name for a stored incomingCallStyle value. Used both here
 * and as SettingsScreen's NavRow subtitle, so both always agree on the
 * display name for a given stored id. Falls back to "Aurora" for anything
 * unrecognized, matching IncomingCallScreen's own dispatcher fallback.
 */
fun incomingCallStyleDisplayName(styleId: String): String = when (styleId) {
    "orbit" -> "Orbit"
    "pulse" -> "Pulse"
    else -> "Aurora"
}

private data class IncomingCallStyleOption(
    val id: String,
    val name: String,
    val description: String
)

private val IncomingCallStyleOptions = listOf(
    IncomingCallStyleOption("aurora", "Aurora", "Soft multi-color glow, swipe up to answer"),
    IncomingCallStyleOption("orbit", "Orbit", "Calm rotating glass rings, drag inward to answer"),
    IncomingCallStyleOption("pulse", "Pulse", "Sonar-style glass ripples, tap the glass pills")
)

/**
 * Settings screen for picking the full-screen incoming-call visual style.
 * Each option renders a small live-animated preview built from the same
 * liquidGlass/glassCard modifiers and hue-derivation the real
 * IncomingCallScreen styles use - deliberately a simplified stand-in
 * (a small backdrop + a small ring/ripple/glow motif) rather than importing
 * the full-size composables, since the actual screen needs real caller data
 * and fills the whole display; this preview only needs to convey each
 * style's character at a glance.
 */
@Composable
fun IncomingCallStylePickerScreen(
    currentStyleId: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

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

        Text(
            "Pick how a full-screen incoming call looks and animates",
            fontSize = 13.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IncomingCallStyleOptions.forEach { option ->
                IncomingCallStyleCard(
                    option = option,
                    palette = palette,
                    selected = option.id == currentStyleId,
                    onClick = { onSelect(option.id) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun IncomingCallStyleCard(
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
        label = "style-card-scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) palette.accent.copy(alpha = .10f) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.accent.copy(alpha = .7f) else palette.cardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StylePreviewThumbnail(styleId = option.id, palette = palette)
        Spacer(Modifier.width(14.dp))
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
                fontSize = 11.5.sp,
                maxLines = 2
            )
        }
        if (selected) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/**
 * Small (76dp) live preview of a style's backdrop + centerpiece motif, so
 * the picker shows each style's actual liquid-glass character instead of
 * just naming it. Deliberately reuses the same accent-hue derivation the
 * real screens use, so a preview under (say) Violet theme actually looks
 * violet-tinted like the real thing would.
 */
@Composable
private fun StylePreviewThumbnail(styleId: String, palette: DialerPalette) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(palette.accent.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.55f)
    hsv[2] = 0.16f
    val backdrop = Color(android.graphics.Color.HSVToColor(hsv))
    val hue = hsv[0]

    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backdrop),
        contentAlignment = Alignment.Center
    ) {
        when (styleId) {
            "orbit" -> OrbitPreviewMotif(hue)
            "pulse" -> PulsePreviewMotif(hue)
            else -> AuroraPreviewMotif(hue)
        }
    }
}

@Composable
private fun AuroraPreviewMotif(hue: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-8).dp, y = (-4).dp)
                .size(38.dp)
                .blur(18.dp)
                .background(Color.hsl(((hue + 25) % 360), 0.7f, 0.65f).copy(alpha = 0.8f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 6.dp)
                .size(42.dp)
                .blur(18.dp)
                .background(Color.hsl(((hue + 240) % 360), 0.65f, 0.5f).copy(alpha = 0.8f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(30.dp)
                .liquidGlass(LocalDialerPalette.current, CircleShape, tintAlpha = 0.35f)
        )
    }
}

@Composable
private fun OrbitPreviewMotif(hue: Float) {
    val transition = rememberInfiniteTransition(label = "orbit-preview-rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "orbit-preview-rotation-value"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .rotate(rotation)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .liquidGlass(LocalDialerPalette.current, CircleShape, tintAlpha = 0.35f)
        )
    }
}

@Composable
private fun PulsePreviewMotif(hue: Float) {
    val transition = rememberInfiniteTransition(label = "pulse-preview-ring")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)),
        label = "pulse-preview-progress"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(28.dp + 30.dp * progress)
                .border(1.dp, Color.White.copy(alpha = (1f - progress) * 0.6f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .liquidGlass(LocalDialerPalette.current, CircleShape, tintAlpha = 0.35f)
        )
    }
}
