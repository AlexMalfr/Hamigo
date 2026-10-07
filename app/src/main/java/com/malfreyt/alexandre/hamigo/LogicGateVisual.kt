package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape

internal enum class LogicGate(val label: String, val mark: String, val inverted: Boolean = false) {
    AND("ET", "&"), OR("OU", "≥1"), NOT("NON", "1", true),
    NAND("NON ET", "&", true), NOR("NON OU", "≥1", true), XOR("OU exclusif", "=1");

    fun output(a: Boolean, b: Boolean): Boolean = when (this) {
        AND -> a && b; OR -> a || b; NOT -> !a
        NAND -> !(a && b); NOR -> !(a || b); XOR -> a != b
    }
}

@Composable internal fun LogicGateTool() {
    var gate by remember { mutableStateOf(LogicGate.AND) }
    var a by remember { mutableStateOf(false) }
    var b by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogicGate.entries.forEach { choice -> FilterChip(gate == choice, { gate = choice }, { Text(choice.label) }) }
        }
        LogicGateDiagram(gate)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(a, { a = !a }, { Text("A = ${if (a) 1 else 0}") })
            if (gate != LogicGate.NOT) FilterChip(b, { b = !b }, { Text("B = ${if (b) 1 else 0}") })
            Spacer(Modifier.weight(1f))
            val output = gate.output(a,b)
            Surface(color = if (output) Teal else Mist, shape = RoundedCornerShape(12.dp)) {
                Text("S = ${if (output) 1 else 0}", Modifier.padding(horizontal=12.dp,vertical=10.dp).testTag("logic-tool-output"), color=if(output) Color.White else Ink, fontWeight=FontWeight.ExtraBold)
            }
        }
        Text("Touche A et B pour changer les entrées et observer la sortie.", color=Muted, fontSize=12.sp, lineHeight=17.sp)
    }
}

/** European rectangular notation. A bubble touches the output, never the function mark. */
@Composable internal fun LogicGateDiagram(gate: LogicGate, modifier: Modifier = Modifier, announceName: Boolean = true) {
    Canvas(modifier.fillMaxWidth().height(96.dp).testTag("logic-gate-${gate.name.lowercase()}").semantics {
        contentDescription = (if (announceName) "Porte ${gate.label}, " else "") + "symbole rectangulaire CEI ${gate.mark}" +
            (if (gate.inverted) ", cercle d’inversion en sortie" else "") +
            if (gate == LogicGate.NOT) ". Entrée A, sortie S." else ". Entrées A et B, sortie S."
    }) {
        val w = size.width; val h = size.height
        val boxWidth = minOf(w * .38f, 100.dp.toPx())
        val left = (w - boxWidth) / 2f; val right = left + boxWidth
        val top = h * .15f; val bottom = h * .85f; val mid = h / 2f
        val stroke = 2.dp.toPx(); val bubble = 5.dp.toPx()
        val wire = minOf(w * .17f, 48.dp.toPx())
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        fun label(text: String, x: Float, y: Float, font: Float) {
            paint.textSize = font
            drawContext.canvas.nativeCanvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
        }
        drawRect(Mist, Offset(left, top), Size(boxWidth, bottom - top))
        drawRect(Teal, Offset(left, top), Size(boxWidth, bottom - top), style = Stroke(stroke))
        label(gate.mark, w / 2f, mid, 26.sp.toPx())
        val inputs = if (gate == LogicGate.NOT) listOf("A" to mid) else listOf("A" to h * .33f, "B" to h * .67f)
        inputs.forEach { (name, y) ->
            drawLine(Teal, Offset(left - wire, y), Offset(left, y), stroke)
            label(name, left - wire - 12.dp.toPx(), y, 14.sp.toPx())
        }
        if (gate.inverted) drawCircle(Teal, bubble, Offset(right + bubble, mid), style = Stroke(stroke))
        val start = right + if (gate.inverted) bubble * 2 else 0f
        drawLine(Teal, Offset(start, mid), Offset(right + wire, mid), stroke)
        label("S", right + wire + 12.dp.toPx(), mid, 14.sp.toPx())
    }
}

@Composable internal fun LogicLearningVisual(visual: String, showNames: Boolean = true, showCaption: Boolean = true) {
    val gates = when (visual) {
        "logic:basic" -> listOf(LogicGate.AND, LogicGate.OR, LogicGate.NOT)
        "logic:inverted" -> listOf(LogicGate.NAND, LogicGate.NOR, LogicGate.XOR)
        "logic:nand-nor" -> listOf(LogicGate.NAND, LogicGate.NOR)
        else -> LogicGate.entries.filter { visual == "logic:${it.name.lowercase()}" }
    }
    if (gates.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        gates.forEach { gate ->
            if (showNames) Text(gate.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teal)
            LogicGateDiagram(gate, announceName=showNames)
        }
        if (showCaption) Text("Symboles rectangulaires CEI · entrées à gauche, sortie à droite.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
