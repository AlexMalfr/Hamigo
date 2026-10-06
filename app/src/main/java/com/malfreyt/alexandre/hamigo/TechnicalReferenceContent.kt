package com.malfreyt.alexandre.hamigo

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The captions use exactly the same horizontal positions as the resistor's painted bands. */
@Composable fun ResistorReadingGuide() {
    var five by remember { mutableStateOf(false) }
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Lire les anneaux", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Place la bande de tolérance, souvent un peu isolée, à droite. Lis la valeur de gauche à droite.", fontSize = 13.sp, lineHeight = 18.sp, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!five, { five = false }, { Text("4 anneaux") })
                FilterChip(five, { five = true }, { Text("5 anneaux") })
            }
            val bands = if (five) listOf("Brun", "Rouge", "Orange", "Brun", "Brun") else listOf("Jaune", "Violet", "Rouge", "Or")
            val captions = if (five) listOf("Chiffre\n1", "Chiffre\n2", "Chiffre\n3", "Facteur\n×10", "Tolérance\n±1 %") else listOf("Chiffre\n4", "Chiffre\n7", "Facteur\n×100", "Tolérance\n±5 %")
            ResistorVisual(bands)
            BoxWithConstraints(Modifier.fillMaxWidth().height(53.dp)) {
                bands.indices.forEach { index ->
                    val fraction = if (index == bands.lastIndex) .79f else .19f + index * if (five) .145f else .20f
                    val center = maxWidth * (.14f + .72f * fraction) + 4.5.dp
                    Text(captions[index], Modifier.offset(x = center - 29.dp).width(58.dp), color = if (index == bands.lastIndex) Color(0xFF8B6832) else Teal,
                        fontSize = if (five) 9.sp else 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Text(if (five) "123 × 10 = 1 230 Ω = 1,23 kΩ" else "47 × 100 = 4 700 Ω = 4,7 kΩ", color = Teal, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Surface(color = Color(0xFFFFEACA), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("À part : la précision", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Ink)
                    Text(if (five) "La dernière bande brune autorise ±1 %, soit 1 217,7 à 1 242,3 Ω." else "La bande or autorise ±5 %, soit 4 465 à 4 935 Ω.", fontSize = 12.sp, lineHeight = 17.sp, color = Ink)
                }
            }
        }
    }
}

/** Native drawings remain crisp when the font size or screen density changes. No source raster is embedded. */
@Composable fun TechnicalReferenceDiagram(row: RefRow) {
    if (row.visual !in technicalDiagramKinds) return
    val kind = row.visual
    val legend = when (kind) {
        "ohm", "power", "power-resistor" -> "U : tension aux bornes · I : courant dans la résistance"
        "energy" -> "P : puissance constante · t : durée · E : énergie transférée"
        "charge" -> "Q : charge déplacée · I : débit · t : durée"
        "series-r", "series-c", "series-l", "parallel-r", "parallel-c" -> if (kind.startsWith("parallel")) "Branches en parallèle : mêmes bornes, même tension" else "En série : le même courant traverse tous les composants"
        "divider" -> "Us est mesurée sur R₂ ; aucune charge n’est branchée à la sortie"
        "reactance-l" -> "Bobine : XL augmente quand la fréquence f augmente"
        "reactance-c" -> "Condensateur : XC diminue quand la fréquence f augmente"
        "rlc" -> "Série : résistance R et opposition nette XL − XC"
        "resonance" -> "À f₀ : XL = XC · les réactances se compensent"
        "rc-charge" -> "À t = τ : 63 % de la tension finale · à 5τ : environ 99 %"
        "period" -> "Une période T : un cycle complet · f : cycles par seconde"
        "sine" -> "Ucrête : maximum · Ueff : même effet thermique qu’en continu"
        "wavelength" -> "λ : distance entre deux points identiques de l’onde"
        "transformer" -> "Primaire : Np spires · secondaire : Ns spires · puissance idéale conservée"
        "gain-power" -> "Pe et Ps : puissances d’entrée et de sortie dans la même unité"
        "gain-voltage" -> "Ue et Us : tensions · mêmes résistances pour comparer les puissances"
        "reflection" -> "Z₀ : ligne · Rcharge : charge · Γ : amplitude de réflexion"
        "efficiency" -> "Puissance utile + pertes = puissance absorbée"
        "am-bandwidth" -> "Deux bandes latérales encadrent la porteuse fc"
        "fm-bandwidth" -> "Enveloppe schématique estimée par Carson · Δf : déviation · fmax : fréquence modulante maximale"
        "quality" -> "Puissance relative : à −3 dB, elle vaut la moitié du maximum · B : largeur autour de f₀"
        "link" -> "Les gains s’additionnent ; les pertes se retranchent"
        else -> "Repère les grandeurs avant de calculer"
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.fillMaxWidth().height(139.dp).semantics { contentDescription = "Schéma de ${row.term}. $legend" }) {
            TechnicalSketch(kind)
        }
        Text(legend, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

private val technicalDiagramKinds = setOf(
    "ohm", "power", "power-resistor", "energy", "charge", "series-r", "parallel-r", "series-c", "parallel-c", "series-l",
    "divider", "reactance-l", "reactance-c", "rlc", "resonance", "rc-charge", "period", "sine", "wavelength", "transformer",
    "gain-power", "gain-voltage", "reflection", "efficiency", "am-bandwidth", "fm-bandwidth", "quality", "link"
)

private fun DrawScope.TechnicalSketch(kind: String) {
    val w = size.width; val h = size.height
    val ink = Ink; val teal = Teal; val orange = Color(0xFFD48735)
    val stroke = 2.dp.toPx()
    fun at(x: Float, y: Float) = Offset(w * x, h * y)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = teal) = drawLine(color, at(x1, y1), at(x2, y2), stroke)
    fun text(value: String, x: Float, y: Float, color: Color = ink, small: Boolean = false) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = (if (small) 10.sp else 12.sp).toPx(); textAlign = Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.drawText(value, w * x, h * y, paint)
    }
    fun arrow(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = teal) {
        line(x1, y1, x2, y2, color)
        val end = at(x2, y2); val start = at(x1, y1)
        val direction = end - start
        val length = kotlin.math.hypot(direction.x, direction.y).coerceAtLeast(1f)
        val ux = direction.x / length; val uy = direction.y / length
        val tip = 7.dp.toPx()
        drawLine(color, end, end - Offset(ux * tip - uy * tip / 2, uy * tip + ux * tip / 2), stroke)
        drawLine(color, end, end - Offset(ux * tip + uy * tip / 2, uy * tip - ux * tip / 2), stroke)
    }
    fun resistor(x: Float, y: Float, width: Float = .18f, vertical: Boolean = false) {
        val rectSize = if (vertical) Size(16.dp.toPx(), h * width) else Size(w * width, 16.dp.toPx())
        val origin = at(x, y) - Offset(rectSize.width / 2, rectSize.height / 2)
        drawRect(Color.White, origin, rectSize)
        drawRect(teal, origin, rectSize, style = Stroke(stroke))
    }
    fun capacitor(x: Float, y: Float) {
        val gap = .012f
        line(x - gap, y - .11f, x - gap, y + .11f)
        line(x + gap, y - .11f, x + gap, y + .11f)
    }
    fun coil(x: Float, y: Float, width: Float = .19f) {
        val path = Path()
        val left = x - width / 2
        path.moveTo(w * left, h * y)
        for (i in 1..80) {
            val fraction = i / 80f
            path.lineTo(w * (left + width * fraction), h * (y - .075f * sin(PI.toFloat() * 8 * fraction).coerceAtLeast(0f)))
        }
        drawPath(path, teal, style = Stroke(stroke))
    }
    fun block(title: String, x: Float, y: Float, width: Float = .22f) {
        val origin = at(x - width / 2, y - .12f)
        drawRoundRect(Mist, origin, Size(w * width, h * .24f), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        text(title, x, y + .035f)
    }
    fun axes() { arrow(.09f, .84f, .94f, .84f); arrow(.09f, .84f, .09f, .12f) }
    when (kind) {
        "ohm", "power", "power-resistor" -> {
            line(.12f, .33f, .84f, .33f); line(.12f, .75f, .84f, .75f)
            line(.84f, .33f, .84f, .75f); resistor(.5f, .33f, .21f)
            line(.12f, .33f, .12f, .48f); line(.12f, .60f, .12f, .75f)
            line(.075f, .48f, .165f, .48f); line(.093f, .60f, .147f, .60f)
            text("+", .19f, .49f); text("−", .19f, .65f)
            arrow(.25f, .19f, .4f, .19f); text("I (A)", .29f, .12f, small = true)
            text("R (Ω)", .50f, .54f); text("U (V)", .86f, .16f, small = true)
            if (kind != "ohm") text(if (kind == "power") "P = U × I (W)" else "P dissipée : chaleur (W)", .51f, .94f, orange)
        }
        "energy", "charge" -> {
            text(if (kind == "energy") "Puissance P (W)" else "Courant I (A)", .2f, .24f)
            block(if (kind == "energy") "P × t" else "I × t", .49f, .52f)
            arrow(.08f, .52f, .36f, .52f); arrow(.62f, .52f, .90f, .52f)
            text("Durée t (s)", .49f, .83f)
            text(if (kind == "energy") "Énergie E (J)" else "Charge Q (C)", .76f, .24f)
        }
        "series-r", "series-c", "series-l" -> {
            line(.06f, .52f, .94f, .52f)
            val symbol = kind.takeLast(1).uppercase()
            listOf(.31f, .69f).forEachIndexed { index, x ->
                when (symbol) { "R" -> resistor(x, .52f); "C" -> { drawRect(Color.White, at(x - .012f, .39f), Size(w * .024f, h * .26f)); capacitor(x, .52f) }; else -> { drawRect(Color.White, at(x - .095f, .4f), Size(w * .19f, h * .2f)); coil(x, .52f) } }
                text("$symbol${if (index == 0) "₁" else "₂"}", x, .30f)
            }
            arrow(.07f, .77f, .30f, .77f); text("Même courant I", .62f, .81f)
        }
        "parallel-r", "parallel-c" -> {
            line(.06f, .5f, .2f, .5f); line(.8f, .5f, .94f, .5f)
            line(.2f, .26f, .2f, .72f); line(.8f, .26f, .8f, .72f)
            listOf(.26f, .72f).forEachIndexed { index, y ->
                line(.2f, y, .8f, y)
                if (kind == "parallel-r") resistor(.5f, y, .21f) else { drawRect(Color.White, at(.488f, y - .13f), Size(w * .024f, h * .26f)); capacitor(.5f, y) }
                text("${if (kind == "parallel-r") "R" else "C"}${if (index == 0) "₁" else "₂"}", .62f, y - .075f)
            }
            text("Même tension U", .5f, .95f)
        }
        "divider" -> {
            line(.44f, .12f, .44f, .90f); resistor(.44f, .32f, .22f, true); resistor(.44f, .69f, .22f, true)
            text("R₁", .6f, .35f); text("R₂", .6f, .72f)
            line(.44f, .5f, .85f, .5f); drawCircle(teal, 3.dp.toPx(), at(.44f, .5f))
            line(.44f, .90f, .85f, .90f)
            arrow(.18f, .89f, .18f, .13f); text("Ue", .1f, .53f)
            arrow(.85f, .87f, .85f, .54f); text("Us", .93f, .75f)
            text("0 V", .62f, .99f, small = true)
        }
        "reactance-l", "reactance-c", "rlc", "resonance" -> {
            line(.05f, .48f, .95f, .48f)
            if (kind == "rlc") { resistor(.23f, .48f, .14f); text("R", .23f, .24f) }
            if (kind != "reactance-c") { drawRect(Color.White, at(.385f, .36f), Size(w * .19f, h * .23f)); coil(.48f, .48f); text("L", .48f, .23f) }
            if (kind != "reactance-l") { val x = if (kind == "reactance-c") .48f else .77f; drawRect(Color.White, at(x - .012f, .33f), Size(w * .024f, h * .31f)); capacitor(x, .48f); text("C", x, .23f) }
            text("Signal sinusoïdal de fréquence f", .49f, .84f, small = true)
        }
        "rc-charge" -> {
            axes()
            val path = Path(); path.moveTo(w * .09f, h * .84f)
            for (i in 1..120) { val t = i / 120f * 5; path.lineTo(w * (.09f + .80f * t / 5), h * (.84f - .61f * (1 - exp(-t)))) }
            drawPath(path, orange, style = Stroke(3.dp.toPx()))
            line(.25f, .84f, .25f, .455f, orange); text("τ", .25f, .98f); text("63 %", .41f, .48f, orange)
            text("Tension Uc", .25f, .15f, small = true); text("Temps", .82f, .98f, small = true)
        }
        "period", "sine", "wavelength" -> {
            val geometry = SineReferenceGeometry
            line(.05f, geometry.baseline, .95f, geometry.baseline, Muted)
            val path = Path()
            for (i in 0..180) { val fraction = i / 180f; val x = geometry.left + geometry.span * fraction; val point = at(x, geometry.ordinate(x)); if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            drawPath(path, teal, style = Stroke(3.dp.toPx()))
            if (kind == "sine") {
                arrow(geometry.secondPeakX, geometry.baseline, geometry.secondPeakX, geometry.crestY, orange)
                line(geometry.secondPeakX, geometry.crestY, .72f, geometry.crestY, orange)
                text("Ucrête", .8f, .23f, orange, true)
                line(.07f, geometry.effectiveY, .94f, geometry.effectiveY, orange)
                text("Ueff", .79f, .45f, orange, true)
            } else {
                line(geometry.firstPeakX, .86f, geometry.secondPeakX, .86f)
                listOf(geometry.firstPeakX, geometry.secondPeakX).forEach { x -> line(x, .82f, x, .90f) }
                text(if (kind == "period") "T (secondes)" else "λ (mètres)", (geometry.firstPeakX + geometry.secondPeakX) / 2, .99f)
            }
        }
        "transformer" -> {
            listOf(.31f, .68f).forEach { x ->
                for (i in 0..3) drawArc(teal, if (x < .5f) -90f else 90f, 180f, false, at(x - .055f, .23f + i * .14f), Size(w * .11f, h * .14f), style = Stroke(stroke))
                val terminalX = if (x < .5f) .13f else .87f
                line(terminalX, .23f, x, .23f); line(terminalX, .79f, x, .79f)
            }
            line(.46f, .23f, .46f, .83f); line(.53f, .23f, .53f, .83f)
            text("Np", .27f, .17f); text("Ns", .73f, .17f)
            text("Up · Ip", .23f, .96f); text("Us · Is", .76f, .96f)
        }
        "gain-power", "gain-voltage", "efficiency" -> {
            block(if (kind == "efficiency") "Convertisseur" else "Bloc G (dB)", .5f, .50f, .40f)
            arrow(.05f, .5f, .28f, .5f); arrow(.72f, .5f, .94f, .5f)
            text(if (kind == "gain-voltage") "Ue (V)" else if (kind == "efficiency") "P absorbée" else "Pe (W)", .18f, .26f, small = true)
            text(if (kind == "gain-voltage") "Us (V)" else if (kind == "efficiency") "P utile" else "Ps (W)", .82f, .26f, small = true)
            if (kind == "efficiency") { arrow(.5f, .63f, .5f, .83f, orange); text("Pertes : chaleur", .5f, .97f, orange, true) }
            else text(if (kind == "gain-voltage") "Résistances égales" else "Sortie / entrée", .5f, .86f, small = true)
        }
        "reflection" -> {
            line(.08f, .35f, .8f, .35f); line(.08f, .74f, .8f, .74f); line(.80f, .35f, .80f, .74f)
            resistor(.80f, .55f, .24f, true); text("Rcharge", .79f, .94f, small = true); text("Ligne Z₀", .36f, .93f)
            arrow(.12f, .46f, .59f, .46f); text("Onde incidente", .38f, .24f, small = true)
            arrow(.59f, .66f, .12f, .66f, orange); text("Onde réfléchie Γ", .34f, .12f, orange, true)
        }
        "am-bandwidth", "fm-bandwidth", "quality" -> {
            axes()
            if (kind == "quality") {
                text("Puissance relative", .32f, .12f, small = true)
                val path = Path(); path.moveTo(w * .09f, h * .83f)
                for (i in 1..100) { val x = i / 100f; val peak = 1f / (1f + ((x - .5f) * 11f) * ((x - .5f) * 11f)); path.lineTo(w * (.09f + .8f * x), h * (.84f - .66f * peak)) }
                drawPath(path, teal, style = Stroke(3.dp.toPx()))
                val leftHalfPower = .09f + .8f * (.5f - 1f / 11f)
                val rightHalfPower = .09f + .8f * (.5f + 1f / 11f)
                line(leftHalfPower, .51f, rightHalfPower, .51f, orange)
                text("B à −3 dB", .49f, .68f, orange, true); text("f₀", .49f, .98f)
            } else {
                if (kind == "am-bandwidth") line(.5f, .84f, .5f, .18f)
                drawRect(teal.copy(alpha = .22f), at(.21f, .48f), Size(w * .28f, h * .36f)); drawRect(teal.copy(alpha = .22f), at(.51f, .48f), Size(w * .28f, h * .36f))
                text("fc", .5f, .98f); text(kind.take(2).uppercase(), .79f, .30f)
                text(if (kind == "am-bandwidth") "faudio max" else "Δf + fmax", .28f, .42f, small = true)
                text(if (kind == "am-bandwidth") "faudio max" else "Δf + fmax", .72f, .42f, small = true)
            }
        }
        "link" -> {
            block("Gain", .31f, .49f, .24f); block("Pertes", .69f, .49f, .24f)
            arrow(.04f, .49f, .17f, .49f); arrow(.45f, .49f, .55f, .49f); arrow(.83f, .49f, .96f, .49f)
            text("Pt", .07f, .28f); text("Pr", .93f, .28f)
            text("+ Gt + Gr", .31f, .82f, teal, true); text("− câble − trajet", .69f, .82f, orange, true)
        }
    }
}
