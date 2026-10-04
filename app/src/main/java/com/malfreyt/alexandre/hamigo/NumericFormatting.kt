package com.malfreyt.alexandre.hamigo

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.round

/** Keep the precision tied to the exercise's accepted error, rather than the Float slider. */
fun formatMeasuredNumber(value: Double, tolerance: Double): String {
    if (!value.isFinite()) return "—"
    val precision = tolerance.takeIf { it.isFinite() && it > 0.0 } ?: .01
    val decimals = ceil(-log10(precision)).toInt().coerceAtLeast(0)
    if (decimals > 8 || (abs(value) >= 1e9)) {
        return String.format(Locale.FRANCE, "%.4g", value)
    }
    val rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
    return rounded.stripTrailingZeros().toPlainString().replace('.', ',')
}

fun toleranceLabel(tolerance: Double, unit: String): String {
    val accepted = tolerance.takeIf { it.isFinite() && it >= 0.0 } ?: .01
    // A displayed limit is a promise. Truncate it rather than widening the accepted error,
    // while keeping three useful digits for relative tolerances generated from a calculation.
    val limit = BigDecimal.valueOf(accepted).round(MathContext(3, RoundingMode.DOWN)).stripTrailingZeros()
    val exponent = limit.precision() - limit.scale() - 1
    val amount = (if (exponent in -5..8) limit.toPlainString() else limit.toString()).replace('.', ',')
    return "Précision acceptée : ± $amount${if (unit.isBlank()) "" else " $unit"}."
}

/** Quantize to the stated adjustment step and clamp after rounding, including negative ranges. */
fun snapSliderValue(value: Float, minimum: Float, maximum: Float, step: Float): Float {
    if (!step.isFinite() || step <= 0f || !value.isFinite()) return value.coerceIn(minimum, maximum)
    return (minimum + round((value.toDouble() - minimum) / step) * step).toFloat().coerceIn(minimum, maximum)
}
