package com.bobpan.ailauncher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes

/**
 * Motion tokens (DESIGN §4.6). Durations in ms; easings shared across animations.
 * Tween/spring specs are built at call-sites with explicit generic T to keep inference simple.
 */
object Motion {
    const val DUR_INSTANT = 0
    const val DUR_MICRO   = 120
    const val DUR_SHORT   = 200
    const val DUR_MEDIUM  = 300
    const val DUR_LONG    = 450

    val Standard:      Easing = FastOutSlowInEasing
    val Decelerate:    Easing = LinearOutSlowInEasing
    val Accelerate:    Easing = FastOutLinearInEasing
    val Emphasized:    Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedIn:  Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedOut: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    fun feedbackPulse() = keyframes<Float> {
        durationMillis = 260
        1.0f at 0
        1.08f at 90
        0.98f at 180
        1.0f at 260
    }
}
