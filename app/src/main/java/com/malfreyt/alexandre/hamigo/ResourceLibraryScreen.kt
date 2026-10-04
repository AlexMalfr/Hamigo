package com.malfreyt.alexandre.hamigo

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.WeakHashMap

private class MemoLibraryState {
    val list = LazyListState()
    var search by mutableStateOf("")
    var searching by mutableStateOf(false)
}
/** The model survives opening a fiche; keeping its list state here prevents the back jump. */
private object MemoNavigation {
    private val states = WeakHashMap<AppModel, MemoLibraryState>()
    fun state(model: AppModel) = states.getOrPut(model) { MemoLibraryState() }
}

@Composable fun ResourceLibraryScreen(model: AppModel, content: Content) {
    val state = remember(model) { MemoNavigation.state(model) }
    val categories = content.references.filter { category ->
        category.title.contains(state.search, true) || category.rows.any { it.term.contains(state.search, true) || it.description.contains(state.search, true) }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state.list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { BigTitle("Les petits mémos", "Des repères à retrouver, des outils à manipuler.") }
                IconButton({ state.searching = !state.searching; if (!state.searching) state.search = "" }) {
                    Icon(if (state.searching) Icons.Rounded.Close else Icons.Rounded.Search, if (state.searching) "Fermer la recherche des mémos" else "Rechercher un mémo")
                }
            }
            if (state.searching) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(state.search, { state.search = it }, label = { Text("Rechercher un mémo") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        items(categories, key = { it.id }) { category ->
            Surface(onClick = { model.resource = category }, color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (category.id) {
                        "morse", "morse-rhythm" -> Icons.Rounded.GraphicEq
                        "resistors", "formulas", "decibels", "units" -> Icons.Rounded.Science
                        "bands", "satellite", "propagation" -> Icons.Rounded.SettingsInputAntenna
                        "itu-regions", "callsigns" -> Icons.Rounded.Public
                        else -> Icons.Rounded.Style
                    }
                    Box(Modifier.size(42.dp).background(Mist, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(category.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val tools = category.id in setOf("morse", "morse-rhythm", "resistors", "units", "formulas", "decibels", "bands", "satellite", "propagation")
                        Text(category.rows.size.toString() + " repères" + (if (category.flashcards) " · flashcards" else "") + (if (tools) " · outils" else ""), fontSize = 11.sp, color = Muted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (categories.isEmpty()) item { Text("Aucun mémo trouvé. Essaie un autre mot.", color = Muted, fontSize = 14.sp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ReferenceDetailScreen(model: AppModel, cat: RefCategory) {
    var search by remember(cat.id) { mutableStateOf("") }
    var searching by remember(cat.id) { mutableStateOf(false) }
    val audio = rememberReferenceAudio()
    val context = LocalContext.current
    val rows = cat.rows.filter { it.term.contains(search, true) || it.description.contains(search, true) || it.extra.contains(search, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(Cream).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ model.resource = null }, Modifier.size(40.dp)) { Icon(Icons.Rounded.ArrowBack, "Retour aux mémos") }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(cat.title, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        if (cat.subtitle.isNotBlank()) Text(cat.subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
                    }
                    IconButton({ searching = !searching; if (!searching) search = "" }, Modifier.size(40.dp)) {
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Fermer le filtre de la fiche" else "Filtrer cette fiche")
                    }
                }
                if (searching) OutlinedTextField(search, { search = it }, label = { Text("Filtrer cette fiche") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (cat.flashcards) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({
                            val cards = model.content?.flashcards.orEmpty().filter { it.topic == cat.id }
                            val reviews = model.progress.reviews
                            val now = System.currentTimeMillis()
                            val selected = cards.sortedWith(compareBy<Question> { (reviews[it.id]?.due ?: 0L) > now }.thenBy { reviews[it.id]?.due ?: 0L }).take(12)
                            model.startQuestions(cat.title, selected)
                        }, Modifier.weight(1.1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
                            Text("Réviser avec les flashcards", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                        }
                        OutlinedButton({
                            val cards = model.content?.flashcards.orEmpty().filter { it.topic == cat.id }.shuffled().take(24)
                            model.startQuestions(cat.title + " · hasard", cards)
                        }, Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
                            Text("Ordre aléatoire", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        if (cat.id == "resistors") item { ResistorReadingGuide() }
        if (hasReferenceTools(cat.id)) item { ResourceInteractiveTools(cat.id) }
        if (cat.id == "itu-regions") item { ItuRegionsMap() }
        if (cat.id == "callsigns") item {
            Text("Les préfixes français et quelques repères internationaux. Ce mémo n’est pas un annuaire mondial exhaustif. Touche 📍 pour situer un territoire.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        items(rows, key = { it.term }) { row ->
            val speak: (() -> Unit)? = when (cat.id) {
                "nato" -> ({ audio.spell(row.description) })
                "morse" -> ({ audio.morse(row.description) })
                "morse-rhythm" -> if (isMorseNotation(row.description)) ({ audio.morse(row.description) }) else null
                "qcodes", "abbreviations" -> ({ audio.morse(MorseReference.encode(row.term.substringBefore(" / ").substringBefore(" papier")).output) })
                else -> null
            }
            val rowContent: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (cat.id == "morse") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val name = MorseReference.characterName(row.term)
                                    Text(name, fontSize = if (name == row.term) 26.sp else 13.sp, lineHeight = if (name == row.term) 29.sp else 17.sp,
                                        fontWeight = FontWeight.ExtraBold, color = Teal, modifier = Modifier.widthIn(min = 30.dp, max = 96.dp))
                                    MorseVisual(row.description, Modifier.weight(1f))
                                }
                            } else if (cat.id == "resistors") {
                                val bands = resistorReferenceBands(row.term)
                                if (bands.size > 1) {
                                    ResistorVisual(bands)
                                    Text(row.term, fontSize = 13.sp, color = Muted)
                                    Text(row.description, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (bands.size == 1) ReferenceColorSwatch(row.term)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(row.term, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                                            Text(resistorRowExplanation(row.term, row.description), fontSize = 13.sp, color = Muted, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(row.term, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Teal)
                                    if (cat.id == "morse-rhythm" && row.term in listOf("Point", "Trait")) MorseVisual(if (row.term == "Point") "." else "-", compact = true)
                                }
                                MorseAwareText(row.description, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                        if (speak != null) IconButton(speak, Modifier.size(44.dp)) { Icon(Icons.Rounded.VolumeUp, "Écouter " + row.term) }
                        if (cat.id == "callsigns") callsignMapQuery(row.term)?.let { location ->
                            IconButton({ openLink(context, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(location)) }, Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.LocationOn, "Situer " + location, tint = Teal)
                            }
                        }
                    }
                    if (row.extra.isNotBlank()) MorseAwareText(row.extra, fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
                }
            }
            if (speak != null) Surface(onClick = speak, color = Color.White, shape = RoundedCornerShape(16.dp)) { rowContent() }
            else Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) { rowContent() }
        }
        if (rows.isEmpty()) item { Text("Aucun repère trouvé dans cette fiche.", color = Muted, fontSize = 14.sp) }
    }
}

@Composable private fun ResistorReadingGuide() {
    Panel(color = Color(0xFFFFF0D7)) {
        Text("Lire les anneaux, dans le bon sens", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Place l’anneau de tolérance, souvent or ou argent et un peu isolé, à droite. Lis ensuite de gauche à droite.", fontSize = 13.sp, lineHeight = 18.sp)
        ResistorVisual(listOf("Jaune", "Violet", "Rouge", "Or"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1 · chiffre", "2 · chiffre", "3 · multiplier", "4 · précision").forEach { Text(it, fontSize = 10.sp, color = Muted) }
        }
        Text("4 et 7 donnent 47. Le rouge multiplie par 100.", fontSize = 13.sp)
        Text("47 × 100 = 4 700 Ω = 4,7 kΩ", color = Teal, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Or : ±5 %. La valeur réelle peut donc aller de 4 465 à 4 935 Ω. Avec 5 anneaux, lis 3 chiffres avant le multiplicateur.", fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private fun resistorRowExplanation(term: String, description: String): String {
    if (term == "Sans anneau de tolérance") return "Tolérance : ±20 %"
    if (term in listOf("Or", "Argent")) return description.replace(" · ", "\n")
    if (term == "5 anneaux") return "Les trois premiers donnent le nombre ; le quatrième le multiplie ; le cinquième donne la tolérance."
    val parts = description.split(" · ")
    return if (parts.firstOrNull()?.toIntOrNull() != null) "Chiffre : " + parts[0] + "\nMultiplicateur : " + parts.getOrElse(1) { "" } +
        (if (parts.size > 2) "\nTolérance : " + parts[2] else "") else description
}
