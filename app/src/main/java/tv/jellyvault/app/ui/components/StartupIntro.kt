package tv.jellyvault.app.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.jellyvault.app.R
import tv.jellyvault.app.ui.theme.jv.JvMotion
import tv.jellyvault.app.ui.theme.jv.rememberAnimatorScale
import kotlin.math.sin

/** The intro plays once per process: coming back to a running app goes straight to the content */
private object StartupIntroOnce {
    var played = false
}

/**
 * Launch screen drawn over the app while it starts: the vector JV logo lit from the inside, a band of light crossing it,
 * looping until [ready] (home rows and their posters loaded), then a fade into an app that is already filled.
 * Bloom = blurred copies of the logo (RenderEffect, Android 12+); the sweep is drawn only on the logo pixels (SrcAtop)
 */
@Composable
fun StartupIntro(
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatorScale = rememberAnimatorScale()
    var visible by remember { mutableStateOf(!StartupIntroOnce.played && animatorScale > 0f) }
    if (!visible && !StartupIntroOnce.played) StartupIntroOnce.played = true
    val currentReady by rememberUpdatedState(ready)

    val reveal = remember { Animatable(0f) }
    val sweep = remember { Animatable(-0.35f) }
    val bloom = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!visible) return@LaunchedEffect
        val start = System.currentTimeMillis()
        coroutineScope {
            launch { reveal.animateTo(1f, tween(IntroTiming.REVEAL_MILLIS, easing = JvMotion.Easing)) }
            launch {
                bloom.animateTo(1f, tween(IntroTiming.BLOOM_RISE_MILLIS, easing = JvMotion.Easing))
                bloom.animateTo(IntroTiming.BLOOM_REST, tween(IntroTiming.BLOOM_SETTLE_MILLIS))
            }
            launch { delay(IntroTiming.MIN_MILLIS) }
            launch {
                delay(IntroTiming.SWEEP_DELAY_MILLIS)
                // A slow first pass, then another one every loop while the app is still starting
                while (true) {
                    sweep.snapTo(-0.35f)
                    sweep.animateTo(1.35f, tween(IntroTiming.SWEEP_MILLIS, easing = LinearEasing))
                    if (currentReady || System.currentTimeMillis() - start > IntroTiming.MAX_MILLIS) break
                    delay(IntroTiming.SWEEP_PAUSE_MILLIS)
                }
            }
        }
        // Never keep the user waiting on the logo once the app is there; a stuck start does not trap them either
        val left = (IntroTiming.MAX_MILLIS - (System.currentTimeMillis() - start)).coerceAtLeast(0L)
        withTimeoutOrNull(left) {
            snapshotFlow { currentReady }.first { it }
            delay(IntroTiming.SETTLE_MILLIS)
        }
        visible = false
        StartupIntroOnce.played = true
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(IntroTiming.FADE_OUT_MILLIS)),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(IntroColors.Night),
        ) {
            val art = painterResource(R.drawable.jv_logo_art)
            val shape = painterResource(R.drawable.jv_logo_shape)
            // Read while drawing only: reading the animations here recomposed the whole intro on every frame, on the
            // main thread the home is composing on underneath
            val pulse = { bloom.value * (1f + IntroTiming.PULSE_DEPTH * sin(sweep.value * Math.PI.toFloat())) }
            // Full-screen layer: a layer the size of the logo would cut the bloom off at its edges
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = reveal.value
                            val s = 0.94f + 0.06f * reveal.value
                            scaleX = s
                            scaleY = s
                        },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .width(IntroTiming.LOGO_WIDTH)
                            .aspectRatio(LOGO_RATIO),
                ) {
                    // Glow from the traced outline, tinted: wide violet, tighter lavender, then a thin bright rim.
                    // The artwork's own colours would wash out towards white once blurred
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        GlowLayer(shape, IntroColors.Violet, 26.dp) { 0.55f * pulse() }
                        GlowLayer(shape, IntroColors.Lavender, 8.dp) { 0.6f * pulse() }
                        GlowLayer(shape, IntroColors.Rim, 3.dp) { 0.5f * pulse() }
                    }
                    // The artwork keeps its glassy shading; the band of light only lights its pixels (SrcAtop)
                    Image(
                        painter = art,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val x = size.width * sweep.value
                                    val band = size.width * 0.22f
                                    drawRect(
                                        brush =
                                            Brush.linearGradient(
                                                0f to Color.Transparent,
                                                0.35f to IntroColors.Cool.copy(alpha = 0.25f),
                                                0.5f to Color.White.copy(alpha = 0.6f),
                                                0.65f to IntroColors.Warm.copy(alpha = 0.22f),
                                                1f to Color.Transparent,
                                                start = Offset(x - band, size.height * 0.1f),
                                                end = Offset(x + band, size.height * 0.9f),
                                            ),
                                        blendMode = BlendMode.SrcAtop,
                                    )
                                },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlowLayer(
    shape: Painter,
    color: Color,
    radius: Dp,
    strength: () -> Float,
) {
    Image(
        painter = shape,
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = strength().coerceIn(0f, 1f)
                    // Per-draw alpha: an offscreen buffer the size of the logo would clip the blur
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.blur(radius, BlurredEdgeTreatment.Unbounded),
    )
}

private object IntroColors {
    /** Black stage, like the system splash before it */
    val Night = Color.Black
    val Violet = Color(0xFF6E3CFF)
    val Lavender = Color(0xFF9664FF)
    val Rim = Color(0xFFD7BEFF)
    val Cool = Color(0xFFB8D4FF)
    val Warm = Color(0xFFFFD9A8)
}

private object IntroTiming {
    /** 224 dp of the 960 dp screen: at 400 then 280 dp the logo still took too much of the screen */
    val LOGO_WIDTH = 224.dp
    const val REVEAL_MILLIS = 900
    const val BLOOM_RISE_MILLIS = 1300
    const val BLOOM_SETTLE_MILLIS = 900
    const val BLOOM_REST = 0.75f
    const val SWEEP_DELAY_MILLIS = 500L
    const val SWEEP_MILLIS = 1800
    const val SWEEP_PAUSE_MILLIS = 700L

    /** Shortest intro, so it reads as a moment and not a flash, and the app has time to warm up behind it */
    const val MIN_MILLIS = 4_000L

    /**
     * Once the home is loaded, the logo stays this much longer: the home draws its first frames and decodes its posters
     * behind it, so the remote moves smoothly from the first press
     */
    const val SETTLE_MILLIS = 1_500L

    /** Longest wait for the home: past it the app shows anyway */
    const val MAX_MILLIS = 15_000L
    const val PULSE_DEPTH = 0.25f
    const val FADE_OUT_MILLIS = 600
}

/** jv_logo_art and jv_logo_shape share the same 960 x 638 crop */
private const val LOGO_RATIO = 960f / 638f
