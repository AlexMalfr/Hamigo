package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

/** Calculation rules kept independent of the UI so their inverses and limits can be checked. */
object ResourceMath {
    fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    fun positive(text: String): Double? = number(text)?.takeIf { it > 0 }
    fun display(value: Double): String {
        if (!value.isFinite()) return "Hors plage"
        if (value == 0.0) return "0"
        val digits = if (abs(value) >= 1e6 || abs(value) < .001) "%.4g" else "%.6f"
        val formatted = String.format(Locale.US, digits, value)
        return (if (digits == "%.6f") formatted.trimEnd('0').trimEnd('.') else formatted).replace('.', ',')
    }
    fun resistance(digits: List<Int>, exponent: Int): Double {
        require(digits.size in 2..3 && digits.first() in 1..9 && digits.all { it in 0..9 } && exponent in -2..9)
        return digits.fold(0) { sum, digit -> sum * 10 + digit } * 10.0.pow(exponent)
    }
    data class ResistanceCode(val digits: List<Int>, val exponent: Int, val value: Double)
    fun encodeResistance(value: Double, significant: Int): ResistanceCode? {
        if (!value.isFinite() || value <= 0 || significant !in 2..3) return null
        var exponent = floor(log10(value)).toInt() - significant + 1
        var mantissa = (value / 10.0.pow(exponent)).roundToInt()
        if (mantissa >= 10.0.pow(significant)) { mantissa /= 10; exponent++ }
        if (exponent !in -2..9) return null
        val digits = mantissa.toString().padStart(significant, '0').map { it.digitToInt() }
        if (digits.first() == 0) return null
        return ResistanceCode(digits, exponent, resistance(digits, exponent))
    }
    fun decibels(ratio: Double, voltage: Boolean = false): Double {
        require(ratio.isFinite() && ratio > 0)
        return (if (voltage) 20.0 else 10.0) * log10(ratio)
    }
    fun ratio(db: Double, voltage: Boolean = false): Double {
        require(db.isFinite())
        return 10.0.pow(db / if (voltage) 20.0 else 10.0)
    }
    /** Difference of logarithms also handles powers whose direct ratio would overflow. */
    fun powerGain(input: Double, output: Double): Double? {
        if (!input.isFinite() || !output.isFinite() || input <= 0 || output <= 0) return null
        return (10.0 * (log10(output) - log10(input))).takeIf { it.isFinite() }
    }
    fun powerAfterGain(input: Double, gainDb: Double): Double? {
        if (!input.isFinite() || input <= 0 || !gainDb.isFinite()) return null
        return 10.0.pow(log10(input) + gainDb / 10.0).takeIf { it.isFinite() && it > 0 }
    }
    data class PowerChain(val afterAmplifier: Double, val output: Double, val totalDb: Double)
    fun powerChain(input: Double, gainDb: Double, lossDb: Double): PowerChain? {
        if (!lossDb.isFinite() || lossDb < 0) return null
        val amplified = powerAfterGain(input, gainDb) ?: return null
        val output = powerAfterGain(amplified, -lossDb) ?: return null
        return PowerChain(amplified, output, gainDb - lossDb)
    }
    fun parallel(resistances: List<Double>): Double {
        require(resistances.isNotEmpty() && resistances.all { it.isFinite() && it > 0 })
        return 1.0 / resistances.sumOf { 1.0 / it }
    }
}

object MorseReference {
    fun characterName(value: String): String = when (value) {
        "." -> "Point final"; "," -> "Virgule"; "?" -> "Point d’interrogation"; "/" -> "Barre oblique"
        "=" -> "Égal"; "+" -> "Plus"; "-" -> "Tiret"; "(" -> "Parenthèse ouvrante"; ")" -> "Parenthèse fermante"
        ":" -> "Deux-points"; "@" -> "Arobase"; "'" -> "Apostrophe"; "\"" -> "Guillemet"
        else -> value
    }
    val alphabet: Map<Char, String> = linkedMapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.", '=' to "-...-", '+' to ".-.-.",
        '-' to "-....-", '(' to "-.--.", ')' to "-.--.-", ':' to "---...", '@' to ".--.-.", '\'' to ".----.",
        '"' to ".-..-.", 'É' to "..-.."
    )
    data class Translation(val output: String, val unsupported: List<String> = emptyList())
    fun encode(text: String): Translation {
        val normalized = Normalizer.normalize(text.uppercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        fun characters(value: String) = value.codePoints().toArray().map { String(Character.toChars(it)) }
        val unsupported = characters(normalized).filter { it.isNotBlank() && (it.length != 1 || it[0] !in alphabet) }.distinct()
        val output = normalized.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" / ") { word ->
            characters(word).map { if (it.length == 1) alphabet[it[0]] ?: "?" else "?" }.joinToString(" ")
        }
        return Translation(output, unsupported)
    }
    fun decode(code: String): Translation {
        val normalized = code.replace('·', '.').replace('•', '.').replace('−', '-').replace('–', '-').replace('—', '-')
        val reverse = alphabet.entries.associate { (letter, signals) -> signals to letter }
        val unsupported = mutableListOf<String>()
        val output = normalized.trim().split('/').joinToString(" ") { word ->
            word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { token ->
                reverse[token]?.toString() ?: "?".also { unsupported += token }
            }
        }
        return Translation(output, unsupported.distinct())
    }
}

private data class BandColor(val name: String, val color: Color)
private val resistorColors = listOf(
    BandColor("Noir", Color(0xFF25262B)), BandColor("Brun", Color(0xFF885331)),
    BandColor("Rouge", Color(0xFFE8453D)), BandColor("Orange", Color(0xFFFF932B)),
    BandColor("Jaune", Color(0xFFFFD447)), BandColor("Vert", Color(0xFF239957)),
    BandColor("Bleu", Color(0xFF377FE0)), BandColor("Violet", Color(0xFF9250B5)),
    BandColor("Gris", Color(0xFF92979F)), BandColor("Blanc", Color.White),
    BandColor("Or", Color(0xFFD6AE47)), BandColor("Argent", Color(0xFFCCD0D5))
)
private fun bandColor(name: String): Color = resistorColors.firstOrNull { it.name.equals(name, true) || (name.equals("Marron", true) && it.name == "Brun") }?.color ?: Color(0xFFE5D5B8)
fun resistorReferenceBands(term: String): List<String> {
    val names = term.substringBefore(" : tolérance").split('·').map { it.trim() }
    return names.takeIf { values -> values.all { value -> resistorColors.any { it.name.equals(value, true) } } } ?: emptyList()
}
private fun multiplierName(exponent: Int) = when (exponent) { -2 -> "Argent"; -1 -> "Or"; else -> resistorColors[exponent].name }
private val toleranceOptions = listOf("Brun" to 1.0, "Rouge" to 2.0, "Vert" to .5, "Bleu" to .25, "Violet" to .1, "Gris" to .05, "Or" to 5.0, "Argent" to 10.0)

@Composable fun ResistorVisual(bands: List<String>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(78.dp).semantics { contentDescription = "Résistance : " + bands.joinToString(", ") }) {
        val cy = size.height / 2
        drawLine(Color(0xFFABB4BA), Offset(0f, cy), Offset(size.width, cy), 5.dp.toPx(), StrokeCap.Round)
        val start = size.width * .14f
        val bodyWidth = size.width * .72f
        val top = cy - 23.dp.toPx()
        drawRoundRect(Color(0xFFE7CFAC), Offset(start, top), Size(bodyWidth, 46.dp.toPx()), CornerRadius(15.dp.toPx()))
        drawRoundRect(Color(0xFFCCAE85), Offset(start, top), Size(bodyWidth, 46.dp.toPx()), CornerRadius(15.dp.toPx()), style = Stroke(1.dp.toPx()))
        bands.forEachIndexed { i, band ->
            val fraction = if (bands.size >= 4 && i == bands.lastIndex) .79f else .19f + i * if (bands.size == 5) .145f else .20f
            drawRect(bandColor(band), Offset(start + bodyWidth * fraction, top), Size(9.dp.toPx(), 46.dp.toPx()))
        }
    }
}

@Composable fun ReferenceColorSwatch(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(28.dp).background(bandColor(name), RoundedCornerShape(7.dp))) {
        Canvas(Modifier.fillMaxSize()) { drawRoundRect(Ink.copy(alpha = .15f), cornerRadius = CornerRadius(7.dp.toPx()), style = Stroke(1.dp.toPx())) }
    }
}

/** Dots and dashes share a centre line and real 1:3 lengths, unlike font punctuation. */
@Composable fun ReferenceMorseSymbols(code: String, modifier: Modifier = Modifier, large: Boolean = true) {
    MorseVisual(code, modifier, compact = !large)
}

private data class ReferenceTool(val id: String, val title: String, val subtitle: String)
private fun categoryTools(category: String): List<ReferenceTool> = when (category) {
    "resistors" -> listOf(ReferenceTool("resistor", "Les anneaux en vrai", "Lire et composer une résistance"), ReferenceTool("networks", "Résistances ensemble", "Série et parallèle"))
    "morse", "morse-rhythm" -> listOf(ReferenceTool("morse", "Le traducteur de Pico", "Texte ↔ Morse, avec le son"))
    "decibels" -> listOf(ReferenceTool("db", "La réglette des décibels", "Rapport ↔ gain ou atténuation"), ReferenceTool("dbchain", "Puissances et décibels", "Entrée → gain → sortie : schéma dynamique"))
    "units", "formulas" -> listOf(ReferenceTool("ohm", "Le trio U, R, I", "Deux valeurs, la troisième se révèle"), ReferenceTool("wavelength", "Une fréquence, une onde", "Fréquence ↔ longueur d’onde"), ReferenceTool("db", "La réglette des décibels", "Rapport ↔ gain ou atténuation"), ReferenceTool("dbchain", "Puissances et décibels", "Entrée → gain → sortie : schéma dynamique"), ReferenceTool("networks", "Résistances ensemble", "Série et parallèle"))
    "propagation", "bands", "satellite" -> listOf(ReferenceTool("wavelength", "Une fréquence, une onde", "Fréquence ↔ longueur d’onde dans le vide"))
    else -> emptyList()
}

fun hasReferenceTools(category: String): Boolean = categoryTools(category).isNotEmpty()

@Composable fun ResourceInteractiveTools(category: String) {
    val tools = remember(category) { categoryTools(category) }
    if (tools.isEmpty()) return
    var opened by remember(category) { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxWidth().padding(vertical = 12.dp),color=Mist,shape=RoundedCornerShape(22.dp)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("À toi de manipuler", fontSize = 12.sp, color = Teal, fontWeight = FontWeight.Bold)
        tools.forEach { tool ->
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                Column {
                Row(Modifier.fillMaxWidth().clickable { opened = if (opened == tool.id) null else tool.id }
                    .padding(horizontal = 12.dp, vertical = 6.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Science, null, tint = Teal, modifier = Modifier.size(22.dp))
                    Text(tool.subtitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    Icon(if (opened == tool.id) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, "Afficher ou réduire l’outil")
                }
                if (opened == tool.id) Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tool.id) {
                    "resistor" -> ResistorCalculator()
                    "morse" -> MorseTranslator()
                    "db" -> DecibelCalculator()
                    "dbchain" -> PowerChainCalculator()
                    "ohm" -> OhmCalculator()
                    "wavelength" -> WavelengthCalculator()
                    "networks" -> ResistanceNetworkCalculator()
                }
                }
                }
            }
        }
    }
    }
}

@Composable private fun ToolChoices(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, name -> FilterChip(selected == index, { onSelect(index) }, label = { Text(name, fontSize = 12.sp) }) }
    }
}

@Composable private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, { onChange(it.take(40)) }, label = { Text(label, fontSize = 12.sp) }, modifier = modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable private fun CalculationResult(value: String, explanation: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
            Text(explanation, fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
        }
    }
}

@Composable private fun InputHint(text: String = "Entre des valeurs strictement positives. La virgule est acceptée.") {
    Text(text, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable private fun BandSelector(label: String, names: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                ReferenceColorSwatch(selected, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(selected, fontSize = 12.sp, modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded, { expanded = false }) {
                names.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false }, leadingIcon = { ReferenceColorSwatch(name) }) }
            }
        }
    }
}

@Composable private fun ResistorCalculator() {
    var mode by remember { mutableIntStateOf(0) }
    var fiveBands by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("4700") }
    var digits by remember { mutableStateOf(listOf(4, 7, 0)) }
    var exponent by remember { mutableIntStateOf(2) }
    var tolerance by remember { mutableStateOf("Or") }
    val digitCount = if (fiveBands) 3 else 2
    val encoding = ResourceMath.positive(input)?.let { ResourceMath.encodeResistance(it, digitCount) }
    val usedDigits = if (mode == 0) digits.take(digitCount) else encoding?.digits ?: digits.take(digitCount)
    val usedExponent = if (mode == 0) exponent else encoding?.exponent ?: exponent
    val bands = usedDigits.map { resistorColors[it].name } + multiplierName(usedExponent) + tolerance
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolChoices(listOf("Couleurs → Ω", "Ω → couleurs"), mode) { mode = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (fiveBands) "5 anneaux · 3 chiffres" else "4 anneaux · 2 chiffres", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(fiveBands, { fiveBands = it }, modifier = Modifier.semantics { contentDescription = "Utiliser cinq anneaux" })
        }
        ResistorVisual(bands)
        if (mode == 0) {
            val labels = (0 until digitCount).map { "Chiffre ${it + 1}" } + "Multiplicateur" + "Tolérance"
            labels.indices.toList().chunked(2).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    indices.forEach { index ->
                        val names = when {
                            index < digitCount -> resistorColors.take(10).drop(if (index == 0) 1 else 0).map { it.name }
                            index == digitCount -> (-2..9).map(::multiplierName)
                            else -> toleranceOptions.map { it.first }
                        }
                        BandSelector(labels[index], names, bands[index], { name ->
                            when {
                                index < digitCount -> digits = digits.toMutableList().also { it[index] = resistorColors.indexOfFirst { color -> color.name == name } }
                                index == digitCount -> exponent = (-2..9).first { multiplierName(it) == name }
                                else -> tolerance = name
                            }
                        }, Modifier.weight(1f))
                    }
                    if (indices.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            NumberField(input, { input = it }, "Résistance en Ω")
            BandSelector("Tolérance", toleranceOptions.map { it.first }, tolerance, { tolerance = it })
        }
        val value = if (mode == 0) ResourceMath.resistance(usedDigits, usedExponent) else encoding?.value
        if (value != null) {
            val percent = toleranceOptions.first { it.first == tolerance }.second
            val mantissa = usedDigits.joinToString("")
            CalculationResult("${ResourceMath.display(value)} Ω ± ${ResourceMath.display(percent)} %", "$mantissa × 10^$usedExponent Ω · ${bands.joinToString(" · ")}")
            if (mode == 1 && abs(value - (ResourceMath.positive(input) ?: value)) > value * 1e-8) InputHint("Valeur arrondie à $digitCount chiffres significatifs pour être codable avec ces anneaux.")
        } else InputHint("Entre une résistance positive codable : multiplicateur entre 10⁻² et 10⁹, avec $digitCount chiffres significatifs.")
        Text("La tolérance est l’écart autorisé autour de la valeur nominale, pas un chiffre supplémentaire.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MorseTranslator() {
    var mode by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("CQ HAMIGO") }
    var code by remember { mutableStateOf("... --- ...") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val translation = if (mode == 0) MorseReference.encode(text) else MorseReference.decode(code)
    val audioCode = if (mode == 0) translation.output else code
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolChoices(listOf("Texte → Morse", "Morse → texte"), mode) { mode = it; stopMorse() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (mode == 0) "Ton message" else "Compose les signaux", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton({
                val pasted = clipboard.getText()?.text.orEmpty()
                if (mode == 0) text = pasted.take(100)
                else if (pasted.isBlank() || isMorseNotation(pasted)) code = normalizedMorse(pasted).take(240)
                else android.widget.Toast.makeText(context, "Colle uniquement des signaux Morse et leurs séparations.", android.widget.Toast.LENGTH_SHORT).show()
                stopMorse()
            }, Modifier.size(36.dp)) { Icon(Icons.Rounded.ContentPaste, "Coller le message", Modifier.size(19.dp)) }
            IconButton({ text = ""; code = ""; stopMorse() }, Modifier.size(36.dp)) {
                Icon(Icons.Rounded.DeleteSweep, "Tout effacer", Modifier.size(21.dp))
            }
        }
        if (mode == 0) {
            OutlinedTextField(text, { text = it.take(100) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                placeholder = { Text("Écris ton message") })
        } else {
            Surface(color = Mist.copy(alpha = .55f), shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(10.dp)) {
                    if (code.isBlank()) Text("Utilise les boutons ci-dessous.", fontSize = 13.sp, color = Muted)
                    else MorseVisual(code, compact = true)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                OutlinedButton({ code = (code + ".").take(240) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { MorseVisual(".", compact = true) }
                OutlinedButton({ code = (code + "-").take(240) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { MorseVisual("-", compact = true) }
                OutlinedButton({ if (code.isNotBlank() && !code.endsWith(' ')) code = (code + " ").take(240) }, Modifier.weight(1.3f), contentPadding = PaddingValues(4.dp)) { Text("Lettre", fontSize = 12.sp) }
                OutlinedButton({ if (code.trim().isNotBlank() && !code.trim().endsWith('/')) code = (code.trimEnd() + " / ").take(240) }, Modifier.weight(1.3f), contentPadding = PaddingValues(4.dp)) {
                    MorseVisual("/", compact = true); Text("Mot", fontSize = 12.sp)
                }
                IconButton({ code = code.dropLast(1); stopMorse() }, Modifier.size(40.dp), enabled = code.isNotEmpty()) { Icon(Icons.Rounded.Backspace, "Effacer le dernier signal", Modifier.size(19.dp)) }
            }
        }
        Surface(color = Mist.copy(alpha = .55f), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Traduction", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    IconButton({ clipboard.setText(AnnotatedString(translation.output)) }, Modifier.size(32.dp), enabled = translation.output.isNotBlank()) {
                        Icon(Icons.Rounded.ContentCopy, "Copier la traduction", Modifier.size(17.dp))
                    }
                }
                if (mode == 0 && translation.output.isNotBlank()) MorseVisual(translation.output, compact = true)
                else Text(translation.output.ifBlank { "Le résultat apparaît ici." }, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Teal)
            }
        }
        if (translation.unsupported.isNotEmpty()) Text(
            if (mode == 0) "Ce message contient des caractères non reconnus. Retire-les pour écouter." else "Une séquence ne correspond pas à un caractère connu. Vérifie les séparations de lettres.",
            color = Color(0xFF9B423B), fontSize = 12.sp)
        if (audioCode.length > 240) InputHint("Raccourcis ton message pour pouvoir l’écouter en entier.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ playMorse(audioCode) }, Modifier.weight(1f), enabled = audioCode.isNotBlank() && audioCode.length <= 240 && translation.unsupported.isEmpty()) {
                Icon(Icons.Rounded.VolumeUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Écouter", fontSize = 12.sp)
            }
            OutlinedButton({ stopMorse() }, Modifier.weight(1f)) { Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Arrêter", fontSize = 12.sp) }
        }
        InputHint("« Lettre » sépare deux caractères ; le séparateur oblique sépare les mots. Point = 1 unité, trait = 3 ; silences : 1, 3 et 7 unités. Les accents sont translittérés.")
        DisposableEffect(Unit) { onDispose { stopMorse() } }
    }
}

@Composable private fun DecibelCalculator() {
    var kind by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("2") }
    var negative by remember { mutableStateOf(false) }
    val voltage = kind == 1
    val magnitude = ResourceMath.number(input)?.takeIf { it >= 0 }
    val number = if (mode == 0) ResourceMath.positive(input) else magnitude?.let { if (negative) -it else it }
    val value = number?.let { if (mode == 0) ResourceMath.decibels(it, voltage) else ResourceMath.ratio(it, voltage) }?.takeIf { it.isFinite() && (mode == 0 || it > 0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolChoices(listOf("Puissance", "Tension"), kind) { kind = it }
        ToolChoices(listOf("Rapport → dB", "dB → rapport"), mode) { mode = it; input = if (it == 0) "2" else "3"; negative = false }
        if (mode == 1) {
            SignedNumberField(input, { input = it }, negative, { negative = it }, "Valeur en dB")
        } else {
            NumberField(input, { input = it }, if (voltage) "U₂ / U₁" else "P₂ / P₁")
        }
        if (value != null) CalculationResult(if (mode == 0) "${ResourceMath.display(value)} dB" else "× ${ResourceMath.display(value)}",
            if (mode == 0) "${if (voltage) "20" else "10"} × log₁₀(${ResourceMath.display(number!!)})" else "10^(${ResourceMath.display(number!!)} / ${if (voltage) "20" else "10"})")
        else InputHint(if (mode == 0) "Le rapport doit être strictement positif : 0 n’a pas de logarithme fini." else "Entre un nombre fini de dB. Une atténuation utilise un signe moins ; les résultats hors plage numérique sont refusés.")
        if (voltage) Text("La formule 20 log₁₀(U₂/U₁) suppose des impédances identiques. Sinon, calcule d’abord les puissances.", fontSize = 12.sp, color = Ink, lineHeight = 17.sp)
        else InputHint("Un rapport supérieur à 1 donne un gain positif ; un rapport inférieur à 1 donne une atténuation négative. Un rapport est sans unité.")
    }
}

@Composable private fun SignedNumberField(value: String, onChange: (String) -> Unit, negative: Boolean, onSign: (Boolean) -> Unit, label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton({ onSign(!negative) }, Modifier.size(48.dp).semantics { contentDescription = if (negative) "Signe moins : passer au plus" else "Signe plus : passer au moins" }) {
            Text(if (negative) "−" else "+", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        NumberField(value, { text ->
            // A pasted signed value also updates the button, but the field always shows the magnitude.
            val trimmed = text.trim()
            if (trimmed.startsWith('-') || trimmed.startsWith('−')) onSign(true)
            if (trimmed.startsWith('+')) onSign(false)
            onChange(trimmed.removePrefix("-").removePrefix("−").removePrefix("+"))
        }, label, Modifier.weight(1f))
    }
}

private fun powerToolDisplay(value: Double) = ResourceMath.display(java.math.BigDecimal.valueOf(value).round(java.math.MathContext(4)).toDouble())

/** Editable measurements and stages share one live schematic; no stored answer is required. */
@Composable private fun PowerChainCalculator() {
    var mode by remember { mutableIntStateOf(0) }
    var unit by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("10") }
    var output by remember { mutableStateOf("20") }
    var gain by remember { mutableStateOf("6") }
    var gainNegative by remember { mutableStateOf(false) }
    var loss by remember { mutableStateOf("3") }
    val unitName = listOf("W", "mW", "µW")[unit]
    val inValue = ResourceMath.positive(input)
    val outValue = ResourceMath.positive(output)
    val gainValue = ResourceMath.number(gain)?.takeIf { it >= 0 }?.let { if (gainNegative) -it else it }
    val lossValue = ResourceMath.number(loss)?.takeIf { it >= 0 }
    val measuredGain = if (inValue != null && outValue != null) ResourceMath.powerGain(inValue, outValue) else null
    val singleOutput = if (inValue != null && gainValue != null) ResourceMath.powerAfterGain(inValue, gainValue) else null
    val chain = if (inValue != null && gainValue != null && lossValue != null) ResourceMath.powerChain(inValue, gainValue, lossValue) else null
    val finalOutput = when (mode) { 0 -> outValue; 1 -> singleOutput; else -> chain?.output }
    val gainDb = if (mode == 0) measuredGain else gainValue
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolChoices(listOf("Mesurer le gain", "Calculer la sortie", "Ajouter un câble"), mode) { mode = it }
        ToolChoices(listOf("W", "mW", "µW"), unit) { next ->
            val previousScale = listOf(1.0, 1e-3, 1e-6)[unit]
            val nextScale = listOf(1.0, 1e-3, 1e-6)[next]
            ResourceMath.positive(input)?.let { input = ResourceMath.display(it * previousScale / nextScale) }
            ResourceMath.positive(output)?.let { output = ResourceMath.display(it * previousScale / nextScale) }
            unit = next
        }
        NumberField(input, { input = it }, "Puissance d’entrée Pe ($unitName)")
        if (mode == 0) NumberField(output, { output = it }, "Puissance de sortie Ps ($unitName)")
        else SignedNumberField(gain, { gain = it }, gainNegative, { gainNegative = it }, "Gain du bloc (dB)")
        if (mode == 2) NumberField(loss, { loss = it }, "Perte du câble (dB, valeur positive)")
        val nodes = if (mode == 2) listOf(
            "Pe", "${inValue?.let(::powerToolDisplay) ?: "?"} $unitName",
            "Après le bloc", "${chain?.afterAmplifier?.let(::powerToolDisplay) ?: "?"} $unitName",
            "Ps", "${chain?.output?.let(::powerToolDisplay) ?: "?"} $unitName"
        ) else listOf("Pe", "${inValue?.let(::powerToolDisplay) ?: "?"} $unitName", "Ps", "${finalOutput?.let(::powerToolDisplay) ?: "?"} $unitName")
        PowerFlowSchematic(nodes.chunked(2).map { it[0] to it[1] },
            if (mode == 2) listOf("${gainDb?.let(::powerToolDisplay) ?: "?"} dB", "−${lossValue?.let(::powerToolDisplay) ?: "?"} dB")
            else listOf("${gainDb?.let(::powerToolDisplay) ?: "?"} dB"))
        when {
            mode == 0 && measuredGain != null -> CalculationResult("G ≈ ${powerToolDisplay(measuredGain)} dB", "10 × log₁₀(Ps/Pe) · ${if (measuredGain >= 0) "gain" else "atténuation"}")
            mode == 1 && singleOutput != null -> CalculationResult("Ps ≈ ${powerToolDisplay(singleOutput)} $unitName", "Pe × 10^(G/10) · les deux puissances utilisent la même unité.")
            mode == 2 && chain != null -> CalculationResult("Ps ≈ ${powerToolDisplay(chain.output)} $unitName", "Bilan : ${ResourceMath.display(gainValue!!)} − ${ResourceMath.display(lossValue!!)} = ${ResourceMath.display(chain.totalDb)} dB")
            else -> InputHint("Les puissances doivent être positives ; le gain peut être nul ou négatif. La perte du câble est une valeur positive ou nulle. Les résultats hors plage sont refusés.")
        }
        Text("Affichage à quatre chiffres significatifs ; calcul sans arrondir les étapes. Les repères du cours (+3 dB ≈ ×2) sont des approximations.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable private fun PowerFlowSchematic(nodes: List<Pair<String, String>>, stages: List<String>) {
    Column(Modifier.fillMaxWidth().background(Mist.copy(alpha = .65f), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        nodes.forEachIndexed { index, (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(value, color = Teal, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (index < stages.size) Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(24.dp), tint = Teal)
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
                    Text(if (index == 0) "Bloc : ${stages[index]}" else "Câble : ${stages[index]}", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Ink, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable private fun OhmCalculator() {
    var target by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("470") }
    var second by remember { mutableStateOf("0,02") }
    val labels = when (target) { 0 -> "Résistance R (Ω)" to "Courant I (A)"; 1 -> "Tension U (V)" to "Résistance R (Ω)"; else -> "Tension U (V)" to "Courant I (A)" }
    val a = ResourceMath.positive(first)
    val b = ResourceMath.positive(second)
    val value = if (a != null && b != null) (if (target == 0) a * b else a / b).takeIf { it.isFinite() && it > 0 } else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolChoices(listOf("Trouver U", "Trouver I", "Trouver R"), target) { target = it; first = if (it == 0) "470" else "9"; second = if (it == 1) "470" else "0,02" }
        NumberField(first, { first = it }, labels.first)
        NumberField(second, { second = it }, labels.second)
        if (value != null) CalculationResult("${listOf("U", "I", "R")[target]} = ${ResourceMath.display(value)} ${listOf("V", "A", "Ω")[target]}", listOf("U = R × I", "I = U / R", "R = U / I")[target])
        else InputHint()
        InputHint("Utilise les unités de base : 20 mA = 0,02 A ; 4,7 kΩ = 4 700 Ω. Ici, on calcule la valeur positive en continu dans une résistance.")
    }
}

@Composable private fun WavelengthCalculator() {
    var mode by remember { mutableIntStateOf(0) }
    var unit by remember { mutableIntStateOf(2) }
    var input by remember { mutableStateOf("145") }
    val number = ResourceMath.positive(input)
    val scale = listOf(1.0, 1e3, 1e6, 1e9)[unit]
    val result = number?.let { if (mode == 0) 299_792_458.0 / (it * scale) else 299_792_458.0 / it / scale }?.takeIf { it.isFinite() && it > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolChoices(listOf("Fréquence → λ", "λ → fréquence"), mode) { mode = it; input = if (it == 0) "145" else "2" }
        ToolChoices(listOf("Hz", "kHz", "MHz", "GHz"), unit) { next ->
            if (mode == 0) ResourceMath.positive(input)?.let { input = ResourceMath.display(it * scale / listOf(1.0, 1e3, 1e6, 1e9)[next]) }
            unit = next
        }
        NumberField(input, { input = it }, if (mode == 0) "Fréquence en ${listOf("Hz", "kHz", "MHz", "GHz")[unit]}" else "Longueur d’onde en mètres")
        if (result != null) CalculationResult(if (mode == 0) "λ = ${ResourceMath.display(result)} m" else "f = ${ResourceMath.display(result)} ${listOf("Hz", "kHz", "MHz", "GHz")[unit]}", "λ = c / f · c = 299 792 458 m/s dans le vide")
        else InputHint()
        Text("L’approximation λ(m) ≈ 300 / f(MHz) aide au calcul mental. La longueur physique d’une antenne dépend aussi de sa forme et du facteur de raccourcissement.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable private fun ResistanceNetworkCalculator() {
    var mode by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("100") }
    var second by remember { mutableStateOf("100") }
    val a = ResourceMath.positive(first)
    val b = ResourceMath.positive(second)
    val value = if (a != null && b != null) (if (mode == 0) a + b else ResourceMath.parallel(listOf(a, b))).takeIf { it.isFinite() && it > 0 } else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolChoices(listOf("En série", "En parallèle"), mode) { mode = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(first, { first = it }, "R₁ (Ω)", Modifier.weight(1f))
            NumberField(second, { second = it }, "R₂ (Ω)", Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().height(75.dp).semantics { contentDescription = if (mode == 0) "Deux résistances bout à bout" else "Deux résistances sur deux branches" }) {
            val y = size.height / 2
            val wire = Muted
            fun resistor(x: Float, cy: Float, w: Float) {
                drawRect(Color.White, Offset(x, cy - 9.dp.toPx()), Size(w, 18.dp.toPx()))
                drawRect(Teal, Offset(x, cy - 9.dp.toPx()), Size(w, 18.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            if (mode == 0) {
                drawLine(wire, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                resistor(size.width * .19f, y, size.width * .20f)
                resistor(size.width * .61f, y, size.width * .20f)
            } else {
                val left = size.width * .15f; val right = size.width * .85f; val gap = 22.dp.toPx()
                drawLine(wire, Offset(0f, y), Offset(left, y), 2.dp.toPx())
                drawLine(wire, Offset(right, y), Offset(size.width, y), 2.dp.toPx())
                drawLine(wire, Offset(left, y - gap), Offset(left, y + gap), 2.dp.toPx())
                drawLine(wire, Offset(right, y - gap), Offset(right, y + gap), 2.dp.toPx())
                listOf(y - gap, y + gap).forEach { cy -> drawLine(wire, Offset(left, cy), Offset(right, cy), 2.dp.toPx()); resistor(size.width * .36f, cy, size.width * .28f) }
            }
        }
        if (value != null) CalculationResult("R = ${ResourceMath.display(value)} Ω", if (mode == 0) "R = R₁ + R₂ · la valeur dépasse chacune des résistances." else "1/R = 1/R₁ + 1/R₂ · la valeur est inférieure à la plus petite résistance.")
        else InputHint()
    }
}
