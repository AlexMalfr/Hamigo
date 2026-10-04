package com.malfreyt.alexandre.hamigo

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.*

enum class CalculatorAngleMode { DEGREES, RADIANS }

/** A bounded local expression evaluator: it does not execute scripts or access the network. */
object CalculatorEngine {
    fun evaluate(expression: String, mode: CalculatorAngleMode = CalculatorAngleMode.DEGREES, answer: Double = 0.0): Double {
        require(expression.length <= 512) { "Expression trop longue." }
        return Parser(expression, mode, answer).evaluate()
    }

    fun format(value: Double): String {
        require(value.isFinite())
        if (value == 0.0) return "0"
        return BigDecimal.valueOf(value).round(MathContext(12)).stripTrailingZeros().toString().replace('.', ',')
    }

    private class Parser(source: String, private val mode: CalculatorAngleMode, private val answer: Double) {
        private val text = source.lowercase(Locale.ROOT).replace('−', '-').replace('×', '*').replace('÷', '/').replace(',', '.').replace("π", "pi")
        private var offset = 0
        private var depth = 0
        fun evaluate(): Double {
            require(text.isNotBlank()) { "Entre un calcul." }
            val value = expression()
            spaces()
            require(offset == text.length) { "Vérifie les parenthèses et les symboles." }
            require(value.isFinite()) { "Résultat hors domaine ou trop grand." }
            return value
        }
        private fun expression(): Double {
            var value = term()
            while (true) value = when {
                consume('+') -> value + term()
                consume('-') -> value - term()
                else -> return value
            }
        }
        private fun term(): Double {
            var value = unary()
            while (true) value = when {
                consume('*') -> value * unary()
                consume('/') -> {
                    val divisor = unary()
                    require(divisor != 0.0) { "Division par zéro." }
                    value / divisor
                }
                implicitMultiplication() -> value * unary()
                else -> return value
            }
        }
        private fun unary(): Double {
            enter()
            try {
                return when {
                    consume('+') -> unary()
                    consume('-') -> -unary()
                    else -> power()
                }
            } finally { depth-- }
        }
        private fun power(): Double {
            var value = primary()
            while (consume('%')) value /= 100.0
            if (consume('^')) value = value.pow(unary())
            return value
        }
        private fun primary(): Double {
            spaces()
            if (consume('(')) {
                val value = expression()
                require(consume(')')) { "Il manque une parenthèse fermante." }
                return value
            }
            val start = offset
            if (text.getOrNull(offset)?.isLetter() == true) {
                while (text.getOrNull(offset)?.isLetter() == true) offset++
                return when (val name = text.substring(start, offset)) {
                    "pi" -> PI
                    "e" -> E
                    "ans" -> answer
                    else -> {
                        require(consume('(')) { "Une fonction s'écrit avec des parenthèses." }
                        val value = expression()
                        require(consume(')')) { "Il manque une parenthèse fermante." }
                        val radians = if (mode == CalculatorAngleMode.DEGREES) Math.toRadians(value) else value
                        val result = when (name) {
                            "sqrt" -> sqrt(value)
                            "abs" -> abs(value)
                            "ln" -> ln(value)
                            "log" -> log10(value)
                            "exp" -> exp(value)
                            "sin" -> sin(radians)
                            "cos" -> cos(radians)
                            "tan" -> {
                                require(abs(cos(radians)) > 1e-12) { "Tangente non définie pour cet angle." }
                                tan(radians)
                            }
                            "asin" -> inverseAngle(asin(value))
                            "acos" -> inverseAngle(acos(value))
                            "atan" -> inverseAngle(atan(value))
                            else -> throw IllegalArgumentException("Fonction inconnue.")
                        }
                        require(result.isFinite()) { "Valeur hors domaine de la fonction." }
                        result
                    }
                }
            }
            while (text.getOrNull(offset)?.let { it.isDigit() || it == '.' } == true) offset++
            // A number such as 1e-6 is accepted; 2e means the constant e multiplied by two.
            if (text.getOrNull(offset) == 'e') {
                val exponentStart = offset
                var scan = offset + 1
                if (text.getOrNull(scan) in listOf('+', '-')) scan++
                val digitsStart = scan
                while (text.getOrNull(scan)?.isDigit() == true) scan++
                if (scan > digitsStart) offset = scan else offset = exponentStart
            }
            require(offset > start) { "Il manque un nombre." }
            return text.substring(start, offset).toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("Nombre invalide.")
        }
        private fun inverseAngle(value: Double) = if (mode == CalculatorAngleMode.DEGREES) Math.toDegrees(value) else value
        private fun implicitMultiplication(): Boolean {
            spaces()
            return text.getOrNull(offset)?.let { it == '(' || it.isLetter() } == true
        }
        private fun consume(character: Char): Boolean {
            spaces()
            if (text.getOrNull(offset) != character) return false
            offset++
            return true
        }
        private fun spaces() { while (text.getOrNull(offset)?.isWhitespace() == true) offset++ }
        private fun enter() { depth++; require(depth <= 64) { "Trop de parenthèses ou de puissances imbriquées." } }
    }
}
