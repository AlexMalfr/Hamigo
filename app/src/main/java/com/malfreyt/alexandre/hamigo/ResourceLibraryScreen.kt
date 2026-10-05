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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.WeakHashMap

private const val courseUrl = "http://f6kgl.free.fr/COURS.html"
private class MemoLibraryState {
    val list = LazyListState()
    var search by mutableStateOf("")
    var searching by mutableStateOf(false)
}
private object MemoNavigation {
    private val states = WeakHashMap<AppModel, MemoLibraryState>()
    fun state(model: AppModel) = states.getOrPut(model) { MemoLibraryState() }
}

@Composable private fun MemoCalculatorLayout(content: @Composable () -> Unit) {
    var calculator by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().imePadding()) {
        content()
        FloatingActionButton({ calculator = true }, Modifier.align(Alignment.BottomEnd).padding(18.dp), containerColor = Teal, contentColor = Color.White) {
            Icon(Icons.Rounded.Calculate, "Ouvrir la calculatrice", Modifier.size(28.dp))
        }
    }
    FloatingCalculator(calculator, { calculator = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ResourceLibraryScreen(model: AppModel, content: Content) {
    val state = remember(model) { MemoNavigation.state(model) }
    val categories = remember(content, state.search) {
        content.references.filter { category ->
            category.title.contains(state.search, true) || category.group.contains(state.search, true) || category.rows.any {
                it.term.contains(state.search, true) || it.description.contains(state.search, true) || it.extra.contains(state.search, true)
            }
        }.groupBy { it.group.ifBlank { "Repères radio" } }
    }
    MemoCalculatorLayout {
        LazyColumn(Modifier.fillMaxSize(), state = state.list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 84.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stickyHeader {
                Column(Modifier.fillMaxWidth().background(Cream).padding(bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mémo", Modifier.weight(1f).testTag("memo-library-title"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        IconButton({ state.searching = !state.searching; if (!state.searching) state.search = "" }) {
                            Icon(if (state.searching) Icons.Rounded.Close else Icons.Rounded.Search, if (state.searching) "Fermer la recherche des mémos" else "Rechercher un mémo")
                        }
                    }
                    if (state.searching) OutlinedTextField(state.search, { state.search = it }, label = { Text("Rechercher un mémo") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
            categories.forEach { (group, fiches) ->
                item(key = "group-$group") { Text(group, Modifier.padding(top = 10.dp, bottom = 2.dp), color = Teal, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                items(fiches, key = { it.id }) { category ->
                    Surface(onClick = { model.resource = category }, color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (category.id) {
                                "morse", "morse-rhythm" -> Icons.Rounded.GraphicEq
                                "resistors", "formulas", "decibels", "units" -> Icons.Rounded.Science
                                "bands", "propagation", "antennas" -> Icons.Rounded.SettingsInputAntenna
                                "itu-regions", "callsigns" -> Icons.Rounded.Public
                                else -> Icons.Rounded.Style
                            }
                            Box(Modifier.size(40.dp).background(Mist, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(category.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("${category.rows.count { it.kind != "flashcard-only" }} repères" + (if (category.flashcards) " · flashcards" else "") + (if (hasReferenceTools(category.id)) " · outils" else ""), fontSize = 11.sp, color = Muted)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            if (categories.isEmpty()) item { Text("Aucun mémo trouvé. Essaie un autre mot.", color = Muted, fontSize = 14.sp) }
            item { CompleteCourseLink() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ReferenceDetailScreen(model: AppModel, cat: RefCategory) {
    var search by remember(cat.id) { mutableStateOf("") }
    var searching by remember(cat.id) { mutableStateOf(false) }
    var region by remember(cat.id) { mutableStateOf("1") }
    val audio = rememberReferenceAudio()
    val rows = remember(cat, search, region) {
        (if (cat.id == "bands") bandRowsForRegion(cat.rows, region) else cat.rows).filter {
            it.kind != "flashcard-only" && (it.term.contains(search, true) || it.description.contains(search, true) || it.extra.contains(search, true) || it.group.contains(search, true))
        }.groupBy { it.group }
    }
    MemoCalculatorLayout {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 84.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (searching) OutlinedTextField(search, { search = it }, label = { Text("Filtrer cette fiche") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (cat.flashcards) ReferenceReviewButtons(model, cat)
                }
            }
            if (cat.intro.isNotBlank()) item { Text(cat.intro, fontSize = 13.sp, lineHeight = 19.sp, color = Muted) }
            if (cat.id == "bands") item { BandRegionPicker(region) { region = it } }
            if (cat.id == "resistors") item { ResistorReadingGuide() }
            if (hasReferenceTools(cat.id)) item { ResourceInteractiveTools(cat.id) }
            if (cat.id == "itu-regions") item { ItuRegionsMap() }
            rows.forEach { (group, entries) ->
                if (group.isNotBlank()) item(key = "section-$group") { Text(group, Modifier.padding(top = 12.dp, bottom = 3.dp), color = Teal, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                items(entries, key = { it.term }) { row -> ReferenceEntry(cat, row, audio) }
            }
            if (rows.isEmpty()) item { Text("Aucun repère trouvé dans cette fiche.", color = Muted, fontSize = 14.sp) }
            item { CompleteCourseLink(cat.source) }
        }
    }
}

@Composable private fun ReferenceReviewButtons(model: AppModel, cat: RefCategory) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({
            val cards = model.content?.flashcards.orEmpty().filter { it.topic == cat.id }
            val reviews = model.progress.reviews
            val now = System.currentTimeMillis()
            val selected = cards.sortedWith(compareBy<Question> { (reviews[it.id]?.due ?: 0L) > now }.thenBy { reviews[it.id]?.due ?: 0L }).take(12)
            if (selected.isNotEmpty()) model.startQuestions(cat.title, selected)
        }, Modifier.weight(1.1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Réviser avec les flashcards", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        }
        OutlinedButton({
            val cards = model.content?.flashcards.orEmpty().filter { it.topic == cat.id }.shuffled().take(24)
            if (cards.isNotEmpty()) model.startQuestions(cat.title + " · hasard", cards)
        }, Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
            Text("Ordre aléatoire", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        }
    }
}

@Composable private fun ReferenceEntry(cat: RefCategory, row: RefRow, audio: ReferenceAudio) {
    val context = LocalContext.current
    val speak: (() -> Unit)? = if (row.kind != "fact") null else when (cat.id) {
        "nato" -> if (row.term.length == 1) ({ audio.spell(row.description) }) else null
        "morse", "morse-rhythm" -> if (isMorseNotation(row.description)) ({ audio.morse(row.description) }) else null
        "qcodes", "abbreviations" -> if (row.term.substringBefore(" / ").matches(Regex("[A-Z0-9]{1,5}"))) ({
            val term = row.term.substringBefore(" / ")
            val code = MorseReference.encode(term).output
            audio.morse(if (term in setOf("AR", "VA")) code.replace(" ", "") else code)
        }) else null
        else -> null
    }
    val background = when (row.kind) { "tip" -> Color(0xFFFFF2CD); "example" -> Color(0xFFEDF0FF); else -> Color.White }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (row.kind in setOf("tip", "example")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (row.kind == "tip") Icons.Rounded.Lightbulb else Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = if (row.kind == "tip") Color(0xFF936411) else Purple)
                Text(if (row.kind == "tip") "Astuce" else "Exemple", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (row.kind == "tip") Color(0xFF936411) else Purple)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (cat.id == "morse" && isMorseNotation(row.description)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(row.term, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Teal, modifier = Modifier.widthIn(min = 30.dp))
                            MorseVisual(row.description, Modifier.weight(1f))
                        }
                    } else if (cat.id == "resistors" && row.kind != "tip" && resistorReferenceBands(row.term).isNotEmpty()) {
                        val bands = resistorReferenceBands(row.term)
                        if (bands.size > 1) {
                            ResistorVisual(bands)
                            Text(row.term, fontSize = 13.sp, color = Muted)
                            Text(row.description, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
                        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReferenceColorSwatch(bands.single())
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(row.term, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                                MorseAwareText(row.description, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(row.term, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Teal)
                            if(cat.id=="morse-rhythm" && row.term in setOf("Point","Trait")) MorseVisual(if(row.term=="Point")"."else"-",compact=true)
                        }
                        MorseAwareText(row.description, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                if (speak != null) IconButton(speak, Modifier.size(44.dp)) { Icon(Icons.Rounded.VolumeUp, "Écouter " + row.term) }
                if (cat.id == "callsigns") radioCallsignMapQuery(row)?.let { location ->
                    IconButton({ openLink(context, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(location)) }, Modifier.size(44.dp)) { Icon(Icons.Rounded.LocationOn, "Situer " + location, tint = Teal) }
                }
            }
            if (row.visual.isNotBlank()) {
                TechnicalReferenceDiagram(row)
                RadioReferenceDiagram(row)
                MemoExtraDiagram(row)
            }
            if (row.extra.isNotBlank() && row.visual !in setOf("report-r", "report-s", "report-t")) MorseAwareText(row.extra, fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
        }
    }
    if (speak != null) Surface(onClick = speak, color = background, shape = RoundedCornerShape(16.dp)) { body() }
    else Surface(color = background, shape = RoundedCornerShape(16.dp)) { body() }
}

@Composable private fun CompleteCourseLink(source: String = "") {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (source.isNotBlank() && source != courseUrl) TextButton({ openLink(context, source) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Source de cette fiche", fontSize = 11.sp, color = Muted) }
        TextButton({ openLink(context, courseUrl) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Consulter le cours complet F6KGL/F5KFF", fontSize = 11.sp, color = Muted) }
    }
}
