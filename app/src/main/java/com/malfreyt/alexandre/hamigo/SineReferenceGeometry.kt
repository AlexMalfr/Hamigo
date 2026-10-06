package com.malfreyt.alexandre.hamigo

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Curve and measurement markers share one normalized coordinate system. */
internal object SineReferenceGeometry {
    const val left = .08f
    const val span = .84f
    const val baseline = .51f
    const val amplitude = .25f
    const val cycles = 2f
    val firstPeakX = left + span * .25f / cycles
    val secondPeakX = left + span * 1.25f / cycles
    val crestY = baseline - amplitude
    val effectiveY = baseline - amplitude / sqrt(2f)
    fun ordinate(x: Float) = baseline - amplitude * sin((x - left) / span * cycles * 2 * PI).toFloat()
}
