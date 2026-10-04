package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun normalizedMorse(code: String): String = code.replace('·', '.').replace('•', '.').replace('●', '.')
    .replace('━', '-').replace('−', '-').replace('–', '-').replace('—', '-')

fun isMorseNotation(text: String): Boolean = text.isNotBlank() && normalizedMorse(text).all { it in ".-/" || it.isWhitespace() }

data class MorseTextPart(val text: String, val code: Boolean)

/** Keep a sentence's full stop out of a displayed signal. Unicode signals are an explicit alphabet. */
fun morseTextParts(text: String): List<MorseTextPart> {
    if (isMorseNotation(text)) return listOf(MorseTextPart(text, true))
    val signals = Regex("(?<![\\p{L}\\p{N}])(?:[•●━]+(?:[ \\t]+[•●━]+|[ \\t]*/[ \\t]*[•●━]+)*|[.·−–—-]+(?:[ \\t]+[.·−–—-]+|[ \\t]*/[ \\t]*[.·−–—-]+)*|/)(?![\\p{L}\\p{N}])")
    val explicitSingle = Regex("(?:[A-Z0-9]\\s*[=:]|(?i:réponses?|code|signal|point|trait)\\s*[=:]|\\d+[.)])\\s*$")
    val context = Regex("(?i)\\b(?:morse|mots?|lettres?|caractères?|signaux|signal)\\b")
    val parts = mutableListOf<MorseTextPart>()
    var cursor = 0
    signals.findAll(text).forEach { match ->
        val prefix = text.substring(0, match.range.first)
        val raw = match.value
        val canonical = normalizedMorse(raw)
        val unicode = raw.any { it in "•●━" }
        val accepted = unicode || canonical.count { it in ".-" } >= 2 || explicitSingle.containsMatchIn(prefix) ||
            (raw == "/" && (context.containsMatchIn(text) || (parts.lastOrNull()?.code == true && text.substring(cursor, match.range.first).isBlank()))) ||
            (match.range.first > 0 && text[match.range.first - 1] in "(«\"'")
        if (!accepted) return@forEach
        var displayed = raw
        // An explicit letter label disambiguates legacy ASCII prose: A : .-. means A plus punctuation,
        // while R : .-. is already R. Pure code strings above are never altered.
        if (!unicode && raw.endsWith('.') && ('/' !in raw) && raw.none(Char::isWhitespace)) {
            val letter = Regex("\\b([A-Z0-9])\\b").findAll(prefix).lastOrNull()?.groupValues?.get(1)?.singleOrNull()
            val expected = letter?.let { MorseReference.alphabet[it] }
            if (expected != null && canonical == expected + ".") displayed = raw.dropLast(1)
        }
        if (displayed.isEmpty()) return@forEach
        if (match.range.first > cursor) parts += MorseTextPart(text.substring(cursor, match.range.first), false)
        parts += MorseTextPart(displayed, true)
        cursor = match.range.first + displayed.length
    }
    if (cursor < text.length) parts += MorseTextPart(text.substring(cursor), false)
    return if (parts.isEmpty()) listOf(MorseTextPart(text, false)) else parts
}

/** Font-independent signals, including the word divider, with an accessible spoken equivalent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun MorseVisual(code: String, modifier: Modifier = Modifier, compact: Boolean = false, color: Color = Teal) {
    val normalized = normalizedMorse(code)
    val tokens = Regex("[.-]+|/|[^.\\-/\\s]+").findAll(normalized).map { it.value }.toList()
    val unit = if (compact) 4.dp else 6.dp
    val height = if (compact) 21.dp else 28.dp
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tokens.forEach { token ->
            if (token.all { it == '.' || it == '-' }) {
                val units = token.sumOf { if (it == '.') 2 else 4 } - 1
                Canvas(Modifier.width((units * unit.value).dp).height(height).semantics {
                    contentDescription = token.map { if (it == '.') "point" else "trait" }.joinToString(", ")
                }) {
                    val u = unit.toPx()
                    var x = u / 2
                    token.forEach { signal ->
                        if (signal == '.') drawCircle(color, u / 2, Offset(x, size.height / 2))
                        else drawLine(color, Offset(x, size.height / 2), Offset(x + 2 * u, size.height / 2), u, StrokeCap.Round)
                        x += if (signal == '.') 2 * u else 4 * u
                    }
                }
            } else if (token == "/") {
                Canvas(Modifier.width(12.dp).height(height).semantics { contentDescription = "séparation entre mots" }) {
                    drawLine(color.copy(alpha = .5f), Offset(size.width * .75f, size.height * .22f), Offset(size.width * .25f, size.height * .78f), 2.dp.toPx(), StrokeCap.Round)
                }
            } else Text(token, color = color, fontSize = if (compact) 14.sp else 18.sp)
        }
    }
}

/** Use for legacy course prose as well as flashcard backs; ASCII stays a storage/audio format. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun MorseAwareText(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp,
    color: Color = Ink, fontWeight: FontWeight? = null, lineHeight: TextUnit = TextUnit.Unspecified) {
    if (isMorseNotation(text)) {
        MorseVisual(text, modifier, compact = fontSize.value <= 16, color = color)
        return
    }
    val parts = morseTextParts(text)
    if (parts.none { it.code }) {
        Text(text, modifier, fontSize = fontSize, color = color, fontWeight = fontWeight, lineHeight = lineHeight)
        return
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        fun plain(value: String): List<String> = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        parts.forEach { part ->
            if (part.code) Box(Modifier.align(Alignment.CenterVertically)) { MorseVisual(part.text, compact = true, color = color) }
            else plain(part.text).forEach { word -> Text(word, fontSize = fontSize, color = color, fontWeight = fontWeight, lineHeight = lineHeight) }
        }
    }
}
