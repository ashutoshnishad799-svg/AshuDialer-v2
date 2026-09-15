package com.ashudialer.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.R
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.theme.AllPalettes
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * THE FIX for onboarding feeling "laggy" and looking like a different app:
 *
 * 1) PERFORMANCE - the previous version painted four full-screen
 *    Modifier.blur(150.dp) glow blobs behind frosted glass on every single
 *    frame, including through the AnimatedContent page-swipe transition.
 *    A 150dp blur radius over the entire screen is one of the most
 *    expensive things Compose can be asked to render every frame - on
 *    mid-range hardware that's exactly what shows up as dropped frames /
 *    jank ("laggy") the moment anything animates on top of it (page swipe,
 *    button press, the pulsing dots indicator). This version has no
 *    full-screen blur at all.
 *
 * 2) VISUAL CONSISTENCY - the previous version hardcoded one specific
 *    palette's colors (Ocean Blue's) directly, regardless of which theme
 *    was actually active, and used a bespoke "aurora glow behind glass"
 *    look that doesn't match any other screen in the app. This version
 *    reads LocalDialerPalette.current like every other screen and builds
 *    its cards with glassCard/glassCircle - the exact same glass
 *    components PermissionsScreen, AddContactScreen, and the rest of the
 *    app already use - so onboarding now looks like the first screen of
 *    THIS app, in whatever theme is actually active, not a detour into a
 *    different visual language.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    currentThemeId: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    // Two pages total: Welcome (what the app is) and Personalize (confirm
    // the active theme, then finish).
    var step by remember { mutableStateOf(0) }
    val totalSteps = 2

    // THE FIX for "app sometimes closes right when I open it": with no
    // BackHandler at all on this screen, a back press here fell through to
    // the system default - which, since MainActivity has nothing else on
    // its own back stack to return to at this point, simply finished the
    // Activity. Onboarding is the very first thing a new install shows, so
    // a stray/accidental back press (easy to hit while the person is still
    // getting oriented) closed the whole app instead of doing something
    // sensible. Now mirrors the same step-back behavior the on-screen
    // "Back" button already has: on the second page, back returns to the
    // first page; only on the first page does back fall through to
    // finishing (still calling onFinished(), i.e. treated the same as
    // tapping "Skip" - not a hard app-close, just leaves onboarding).
    androidx.activity.compose.BackHandler(enabled = true) {
        if (step > 0) step-- else onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .glassCard(palette, corner = 100.dp, tintAlpha = 0.7f)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(palette.accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ASHU DIALER",
                        color = palette.textPrimary.copy(alpha = .90f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = step == 0) {
                    Text(
                        "Skip",
                        color = palette.textSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                step = totalSteps - 1
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Main content area
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)
                    .pointerInput(step, totalSteps) {
                        // Swipe left/right in addition to the Continue/
                        // Previous buttons - a running drag-distance
                        // accumulator rather than judging off a single
                        // pointer event, since onDragEnd is where the
                        // actual left-vs-right decision is made (a
                        // deliberate full swipe, not an accidental nudge).
                        var dragAccumulator = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onDragEnd = {
                                val threshold = 100f
                                when {
                                    dragAccumulator < -threshold -> {
                                        if (step < totalSteps - 1) step++ else onFinished()
                                    }
                                    dragAccumulator > threshold -> {
                                        if (step > 0) step--
                                    }
                                }
                                dragAccumulator = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount
                        }
                    }
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (slideInHorizontally(tween(400, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(250)))
                    },
                    label = "onboarding-transition"
                ) { page ->
                    when (page) {
                        0 -> WelcomePage(palette)
                        else -> PersonalizePage(palette, currentThemeId, onThemeSelected)
                    }
                }
            }

            // Bottom navigation & button
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
                // Dots indicator
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(totalSteps) { index ->
                        val active = index == step
                        val width by animateDpAsState(
                            targetValue = if (active) 32.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.7f),
                            label = "dot-width"
                        )
                        val colorAlpha by animateFloatAsState(
                            targetValue = if (active) 1f else 0.3f, label = "dot-color"
                        )
                        Box(
                            Modifier.padding(horizontal = 4.dp).height(8.dp).width(width)
                                .clip(CircleShape)
                                .background(palette.accent.copy(alpha = colorAlpha))
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(visible = step > 0) {
                        OnboardingSecondaryButton(
                            text = "Back",
                            palette = palette,
                            onClick = { if (step > 0) step-- }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        OnboardingPrimaryButton(
                            text = if (step == totalSteps - 1) "Start Using Ashu Dialer" else "Continue",
                            palette = palette,
                            onClick = {
                                if (step == totalSteps - 1) {
                                    onFinished() // Takes you to the Permissions screen next
                                } else {
                                    step++
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPrimaryButton(text: String, palette: DialerPalette, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "press-scale")

    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(palette.accent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OnboardingSecondaryButton(text: String, palette: DialerPalette, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "press-scale-secondary")

    Box(
        Modifier
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassCard(palette, corner = 100.dp, tintAlpha = 0.55f)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = palette.textPrimary.copy(alpha = 0.90f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun WelcomePage(palette: DialerPalette) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(104.dp)
                .glassCircle(palette, tintAlpha = 0.5f),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxSize(0.62f).clip(CircleShape)) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Meet Ashu Dialer", color = palette.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 38.sp, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "A clean, private, modern calling experience - recents, contacts and dialing, spam callers flagged automatically, and it feels right at home on your phone.",
            color = palette.textSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .glassCard(palette, corner = 20.dp, tintAlpha = 0.45f)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Shield, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Unknown and spam callers flagged before they reach you",
                color = palette.textPrimary.copy(alpha = .82f),
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun PersonalizePage(
    palette: DialerPalette,
    currentThemeId: String,
    onThemeSelected: (String) -> Unit
) {
    val themeCount = AllPalettes.size
    val selectedPalette = AllPalettes.firstOrNull { it.id == currentThemeId }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            "Make it yours",
            color = palette.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.3).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose from the themes already built into Ashu Dialer",
            color = palette.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .glassCard(palette, corner = 22.dp, tintAlpha = 0.42f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        selectedPalette?.let { Brush.linearGradient(listOf(it.swatchStart, it.swatchEnd)) }
                            ?: Brush.linearGradient(listOf(palette.accent, palette.accent.copy(alpha = .45f)))
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = selectedPalette?.displayName ?: "System",
                    color = palette.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$themeCount built-in themes + System • tap any card to preview instantly",
                    color = palette.textSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(palette.cardBackground.copy(alpha = 0.22f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            item(key = "system") {
                val active = currentThemeId == com.ashudialer.app.ui.theme.AUTO_THEME_ID
                OnboardingThemeCard(
                    name = "System",
                    subtitle = "Follows phone",
                    start = palette.textSecondary.copy(alpha = .35f),
                    end = palette.textPrimary.copy(alpha = .72f),
                    accent = palette.accent,
                    selected = active,
                    onClick = { onThemeSelected(com.ashudialer.app.ui.theme.AUTO_THEME_ID) }
                )
            }

            items(AllPalettes, key = { it.id }) { swatch ->
                OnboardingThemeCard(
                    name = swatch.displayName,
                    subtitle = when (swatch.id) {
                        "gradient" -> "Soft gradient"
                        "midnight" -> "AMOLED dark"
                        "ocean" -> "Cool blue"
                        "sunset" -> "Warm glow"
                        "violet" -> "Deep violet"
                        "rosegold" -> "Warm metallic"
                        "darkmode" -> "Pure dark"
                        "white" -> "Clean light"
                        "rainbow" -> "Full spectrum"
                        else -> "Ashu Dialer theme"
                    },
                    start = swatch.swatchStart,
                    end = swatch.swatchEnd,
                    accent = swatch.accent,
                    selected = swatch.id == currentThemeId,
                    onClick = { onThemeSelected(swatch.id) }
                )
            }
        }
    }
}

@Composable
private fun OnboardingThemeCard(
    name: String,
    subtitle: String,
    start: Color,
    end: Color,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.975f
            selected -> 1.01f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "theme-card-scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = .12f) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent.copy(alpha = .75f) else accent.copy(alpha = .16f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(start, end))),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                Modifier
                    .padding(8.dp)
                    .width(52.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = .17f))
                    .border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(10.dp))
            ) {
                Box(
                    Modifier
                        .padding(6.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .72f))
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .85f))
                )
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5A57)))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF34C759)))
                }
            }

            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            name,
            color = LocalDialerPalette.current.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            subtitle,
            color = LocalDialerPalette.current.textSecondary,
            fontSize = 10.5.sp,
            maxLines = 1
        )
    }
}

