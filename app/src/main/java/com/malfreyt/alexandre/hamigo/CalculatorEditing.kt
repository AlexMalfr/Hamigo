package com.malfreyt.alexandre.hamigo

/** Selection offsets are preserved across both keyboard and calculator-key edits. */
data class CalculatorEdit(val text: String, val start: Int = text.length, val end: Int = start)

object CalculatorEditing {
    fun press(input: CalculatorEdit, key: String): CalculatorEdit {
        val from = minOf(input.start, input.end).coerceIn(0, input.text.length)
        val to = maxOf(input.start, input.end).coerceIn(from, input.text.length)
        fun replace(value: String, cursor: Int = value.length, begin: Int = from, finish: Int = to): CalculatorEdit {
            val text = input.text.replaceRange(begin, finish, value)
            return if (text.length > 512) input else CalculatorEdit(text, begin + cursor)
        }
        fun wrap(prefix: String, suffix: String): CalculatorEdit {
            if (from != to) return replace(prefix + input.text.substring(from, to) + suffix)
            val begin = operandStart(input.text, from)
            if (begin != from) return replace(prefix + input.text.substring(begin, from) + suffix, begin = begin)
            return replace(prefix + suffix, prefix.length)
        }
        return when (key) {
            "C" -> CalculatorEdit("")
            "⌫" -> if (from != to) replace("") else if (from > 0) replace("", begin = from - 1) else input
            "(" -> if (from != to) replace("(" + input.text.substring(from, to) + ")") else replace("()", 1)
            ")" -> if (from == to && input.text.getOrNull(from) == ')') CalculatorEdit(input.text, from + 1) else replace(")")
            "x²" -> wrap("(", ")^2")
            "1/x" -> wrap("1/(", ")")
            "√", "10ˣ", "eˣ", "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln" -> {
                val prefix = when (key) { "√" -> "sqrt("; "10ˣ" -> "10^("; "eˣ" -> "exp("; else -> "$key(" }
                if (from != to) replace(prefix + input.text.substring(from, to) + ")") else replace(prefix + ")", prefix.length)
            }
            "EXP" -> replace("e")
            else -> replace(key)
        }
    }

    /** Find the last complete atom, including a function's name and nested parentheses. */
    private fun operandStart(text: String, end: Int): Int {
        if (end == 0) return end
        var start = end
        if (text[end - 1] == ')') {
            var depth = 0
            while (start > 0) {
                start--
                when (text[start]) { ')' -> depth++; '(' -> { depth--; if (depth == 0) break } }
            }
            if (depth != 0) return end
            while (start > 0 && text[start - 1].isLetter()) start--
        } else {
            while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] in ".,π")) start--
            // Include a signed scientific exponent, e.g. 1e-6.
            if (start > 1 && text[start - 1] in "+−-" && text[start - 2] in "eE") {
                start -= 2
                while (start > 0 && (text[start - 1].isDigit() || text[start - 1] in ".,")) start--
            }
        }
        return start
    }
}
