package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A region is a radio-regulations allocation area, not an ITU/CQ contest zone. */
fun bandRowsForRegion(rows: List<RefRow>, region: String): List<RefRow> {
    val selected = region.takeIf { it in setOf("1", "2", "3") } ?: "1"
    return rows.filter { it.region.isBlank() || it.region == selected }
}

@Composable fun BandRegionPicker(region: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val names = linkedMapOf(
        "1" to "Région 1 · Europe, Afrique…",
        "2" to "Région 2 · Amériques",
        "3" to "Région 3 · Asie, Océanie…"
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Région UIT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        Box {
            OutlinedButton(feedbackClick { expanded = true }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                Text(names[region] ?: names.getValue("1"), Modifier.weight(1f), fontSize = 13.sp)
                Icon(Icons.Rounded.ExpandMore, "Choisir la région UIT", Modifier.size(20.dp))
            }
            DropdownMenu(expanded, { expanded = false }) {
                names.forEach { (value, name) ->
                    DropdownMenuItem(text = { Text(name, fontSize = 14.sp) }, onClick = { onChange(value); expanded = false })
                }
            }
        }
        Text(if (region == "3") "Région 3 : repères du cours de novembre 2025. Vérifie les règles locales."
            else "Repères des territoires français : les conditions locales restent applicables.", fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
    }
}

fun hasRadioReferenceDiagram(row: RefRow): Boolean = row.visual in setOf("report-code", "report-r", "report-s", "report-t", "emission-code")

/** Geographic entries get maps; administrative rules and satellite prefixes do not. */
fun radioCallsignMapQuery(row: RefRow): String? = callsignMapQuery(row.term)
    ?: row.takeIf { it.group.startsWith("Préfixes") && it.term != "FX" }
        ?.description?.substringBefore(" (")?.substringBefore(" / ")?.substringBefore(" :")?.removeSuffix(".")

/** Data supplies the scale; the app lays it out as a compact, accessible table. */
@Composable fun RadioReferenceDiagram(row: RefRow) {
    when (row.visual) {
        "report-code" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioCodeTiles(listOf("R" to "Lisibilité\n1 à 5", "S" to "Force\n1 à 9", "T" to "Tonalité\n1 à 9"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportExample("Phonie", "5 9", "R5 · S9", Modifier.weight(1f))
                ReportExample("Morse", "5 9 9", "R5 · S9 · T9", Modifier.weight(1f))
            }
            Text("Le T décrit la note Morse : il disparaît du report en phonie.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        "report-r", "report-s", "report-t" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            row.extra.lineSequence().map { it.split('|', limit = 2) }.filter { it.size == 2 }.forEachIndexed { index, cells ->
                Row(Modifier.fillMaxWidth().background(if (index % 2 == 0) Mist else Color.White, RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(cells[0], Modifier.width(30.dp), color = Teal, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(cells[1], Modifier.weight(1f), color = Ink, fontSize = 13.sp, lineHeight = 17.sp)
                }
            }
        }
        "emission-code" -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            RadioCodeTiles(listOf("J" to "1 · Porteuse\nBLU supprimée", "3" to "2 · Signal\nAnalogique", "E" to "3 · Information\nTéléphonie"))
            Text("J3E : téléphonie en bande latérale unique, à porteuse supprimée.", fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
        }
    }
}

@Composable private fun RadioCodeTiles(values: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { index, (letter, meaning) ->
            Column(Modifier.weight(1f).background(listOf(Mist, Color(0xFFEEE9FC), Color(0xFFE9F1FF))[index], RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 10.dp)
                .semantics { contentDescription = "$letter : ${meaning.replace('\n', ' ')}" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(letter, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
                Text(meaning, fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable private fun ReportExample(title: String, code: String, meaning: String, modifier: Modifier) {
    Column(modifier.background(Cream, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 11.sp, color = Muted)
        Text(code, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
        Text(meaning, fontSize = 12.sp, color = Ink)
    }
}
