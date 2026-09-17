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
                        // Matches the 18dp rounding used on the bottom
                        // nav buttons now (was 100dp, another full pill) -
                        // see OnboardingPrimaryButton's doc comment for
                        // the full reasoning behind moving away from
                        // maximum rounding everywhere.
                        .glassCard(palette, corner = 18.dp, tintAlpha = 0.7f)
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
            // DESIGN FIX for "onboarding looks cartoony": this was
            // CircleShape (a fully rounded pill/capsule) on both this
            // primary button AND the secondary "Back" button below,
            // stacked on top of an already-playful rainbow gradient
            // backdrop, a rounded pill badge up top, and fully circular
            // theme swatches - the combined effect read as candy-like
            // rather than a premium dialer's onboarding. Moderate 18dp
            // corners keep the rounded, friendly character (this is still
            // an onboarding flow, not a settings form) without every
            // single element being a perfect circle/capsule, which is
            // what actually produced the "cartoony" impression - one
            // consistent rounding language across the flow feels crafted,
            // whereas every shape independently being "as round as
            // possible" is what reads as a toy/game UI.
            .clip(RoundedCornerShape(18.dp))
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
            // Matches OnboardingPrimaryButton's corner fix above - same
            // 18dp rounding instead of glassCard's own corner param
            // (which was 100.dp, i.e. also a full pill) so the two
            // buttons in this same row read as one consistent pair
            // rather than two different rounding styles side by side.
            .glassCard(palette, corner = 18.dp, tintAlpha = 0.55f)
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
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(22.dp))
        Text(
            "Make it yours",
            color = palette.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.3).sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Pick a look — you can change it anytime",
            color = palette.textSecondary,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            item(key = "system") {
                OnboardingThemeCard(
                    name = "System",
                    start = palette.textSecondary.copy(alpha = .32f),
                    end = palette.textPrimary.copy(alpha = .68f),
                    accent = palette.accent,
                    selected = currentThemeId == com.ashudialer.app.ui.theme.AUTO_THEME_ID,
                    onClick = { onThemeSelected(com.ashudialer.app.ui.theme.AUTO_THEME_ID) }
                )
            }

            items(AllPalettes, key = { it.id }) { swatch ->
                OnboardingThemeCard(
                    name = swatch.displayName,
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

/**
 * Minimal theme swatch for onboarding: a soft rounded gradient circle,
 * a small check badge when selected, and a single-line label underneath.
 * No mock phone chrome, no subtitle line, no boxed grid background —
 * kept deliberately quiet so the colors themselves do the talking.
 */
@Composable
private fun OnboardingThemeCard(
    name: String,
    start: Color,
    end: Color,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "theme-card-scale"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "theme-card-ring"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(start, end)))
                .border(
                    width = 2.dp,
                    // THE FIX for the onboarding-theme-selection crash
                    // (IllegalArgumentException: alpha = 1.0021328...
                    // outside the range for sRGB): ringAlpha is a
                    // spring()-based animateFloatAsState animating between
                    // 0f and 1f. A spring with dampingRatio < 1
                    // (underdamped - 0.8f here) physically overshoots its
                    // target before settling, so on the way to 1f this
                    // value can briefly read slightly ABOVE 1f (observed:
                    // 1.0021328). Color.copy(alpha = ...) validates its
                    // input is within [0f, 1f] and throws otherwise -
                    // every tap that selected a theme card had a real
                    // chance of hitting exactly that overshot frame and
                    // crashing the whole onboarding flow. coerceIn clamps
                    // the overshoot to a valid alpha without changing how
                    // the spring itself looks or feels.
                    color = accent.copy(alpha = ringAlpha.coerceIn(0f, 1f)),
                    shape = CircleShape
                )
                .padding(3.dp)
                .then(
                    if (selected) Modifier.border(1.5.dp, Color.White.copy(alpha = .55f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            name,
            color = if (selected) LocalDialerPalette.current.textPrimary else LocalDialerPalette.current.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

