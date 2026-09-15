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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalizePage(
    palette: DialerPalette,
    currentThemeId: String,
    onThemeSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Make it yours", color = palette.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a theme now or change it later",
            color = palette.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        // THE FIX for "onboarding says Pick a theme but nothing here is
        // actually tappable": this used to render AllPalettes.take(5) as
        // plain, non-clickable Boxes - display-only, no onClick at all -
        // so the whole page was really just showing whatever theme
        // happened to already be active, with no way to change it from
        // here despite the heading and subtitle both telling the person
        // to pick one. Every palette (not just the first 5 - Rainbow and
        // the others further down the list were previously unreachable
        // from onboarding entirely) is now shown in a wrapping grid, each
        // swatch clickable, calling onThemeSelected immediately so the
        // whole screen re-themes live (palette here is
        // LocalDialerPalette.current, so tapping a swatch really does
        // preview it in place, not just record a choice for later) -
        // matching what "pick a theme now" was actually promising.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(palette, corner = 28.dp, tintAlpha = 0.45f)
                .padding(vertical = 20.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AllPalettes.forEach { swatch ->
                val isActive = swatch.id == currentThemeId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onThemeSelected(swatch.id) }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        Modifier
                            .size(if (isActive) 46.dp else 40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(swatch.swatchStart, swatch.swatchEnd)))
                            .border(
                                width = if (isActive) 2.5.dp else 1.dp,
                                color = if (isActive) palette.textPrimary.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.10f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActive) {
                            Box(
                                Modifier.size(18.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = "Selected", tint = swatch.accent, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        swatch.displayName,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "${palette.displayName} is set as your default\nChange it anytime in settings",
            color = palette.textSecondary.copy(alpha = .85f),
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(44.dp))

        Box(
            Modifier.size(68.dp).glassCircle(palette, tintAlpha = 0.4f),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Star, null, tint = palette.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("You're all set", color = palette.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, letterSpacing = (-0.2).sp)
    }
}
