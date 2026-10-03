package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ResourceLibraryScreen(model: AppModel, content: Content) {
    var search by remember { mutableStateOf("") }
    val categories = content.references.filter { category ->
        category.title.contains(search, true) || category.rows.any { it.term.contains(search, true) || it.description.contains(search, true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BigTitle("Les petits mémos", "Des repères à retrouver, des outils à manipuler.") }
        item { OutlinedTextField(search, { search = it }, label = { Text("Morse, résistances, codes Q…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        items(categories, key = { it.id }) { category ->
            Surface(onClick = { model.resource = category }, color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (category.id) {
                        "morse", "morse-rhythm" -> Icons.Rounded.GraphicEq
                        "resistors", "formulas", "decibels", "units" -> Icons.Rounded.Science
                        "bands", "satellite", "propagation" -> Icons.Rounded.SettingsInputAntenna
                        else -> Icons.Rounded.Style
                    }
                    Box(Modifier.size(42.dp).background(Mist, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(category.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val tools = category.id in setOf("morse", "morse-rhythm", "resistors", "units", "formulas", "decibels", "bands", "satellite", "propagation")
                        Text("${category.rows.size} repères${if (category.flashcards) " · flashcards" else ""}${if (tools) " · outils" else ""}", fontSize = 11.sp, color = Muted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (categories.isEmpty()) item { Text("Aucun mémo trouvé. Essaie un autre mot.", color = Muted, fontSize = 14.sp) }
    }
}

@Composable fun ReferenceDetailScreen(model: AppModel, cat: RefCategory) {
    var search by remember(cat.id) { mutableStateOf("") }
    val rows = cat.rows.filter { it.term.contains(search, true) || it.description.contains(search, true) || it.extra.contains(search, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader(cat.title, cat.subtitle) { model.resource = null } }
        if (cat.flashcards) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    val cards = model.content?.flashcards.orEmpty().filter { it.topic == cat.id }
                    val reviews = model.progress.reviews
                    val now = System.currentTimeMillis()
                    val selected = cards.sortedWith(compareBy<Question> { (reviews[it.id]?.due ?: 0L) > now }.thenBy { reviews[it.id]?.due ?: 0L }).take(12)
                    model.startQuestions(cat.title, selected)
                }, Modifier.weight(1.1f).heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Réviser avec les flashcards", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                }
                OutlinedButton({
                    val cards = model.content?.flashcards.orEmpty().filter { it.topic == cat.id }.shuffled().take(24)
                    model.startQuestions("${cat.title} · hasard", cards)
                }, Modifier.weight(1f).heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
                    Text("Ordre aléatoire", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                }
            }
        }
        item { ResourceInteractiveTools(cat.id) }
        item { OutlinedTextField(search, { search = it }, label = { Text("Filtrer cette fiche") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (cat.id == "resistors") item {
            Panel(color = Color(0xFFFFF0D7)) {
                ResistorVisual(listOf("Jaune", "Violet", "Rouge", "Or"))
                Text("Jaune 4 · violet 7 · rouge ×100 · or ±5 %", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("47 × 100 = 4 700 Ω ± 5 %", color = Teal, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        items(rows) { row ->
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (cat.id == "morse") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(row.term, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Teal, modifier = Modifier.widthIn(min = 30.dp))
                            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) { ReferenceMorseSymbols(row.description) }
                            IconButton({ playMorse(row.description) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.VolumeUp, "Écouter le Morse ${row.term}") }
                        }
                    } else if (cat.id == "resistors") {
                        val bands = resistorReferenceBands(row.term)
                        if (bands.size > 1) {
                            ResistorVisual(bands)
                            Text(row.term, fontSize = 13.sp, color = Muted)
                            Text(row.description, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (bands.size == 1) ReferenceColorSwatch(row.term)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(row.term, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                                    Text(row.description, fontSize = 14.sp, color = Muted, lineHeight = 19.sp)
                                }
                            }
                        }
                    } else {
                        if (cat.id == "morse-rhythm" && row.term in listOf("Point", "Trait")) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(row.term, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Teal)
                                ReferenceMorseSymbols(if (row.term == "Point") "." else "-")
                            }
                        } else Text(row.term, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Teal)
                        Text(row.description, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    if (row.extra.isNotBlank()) Text(row.extra, fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
                }
            }
        }
        if (rows.isEmpty()) item { Text("Aucun repère trouvé dans cette fiche.", color = Muted, fontSize = 14.sp) }
    }
}
