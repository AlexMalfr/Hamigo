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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val courseUrl = "http://f6kgl.free.fr/COURS.html"
internal class MemoLibraryState(val list: LazyListState = LazyListState()) {
    var search by mutableStateOf("")
    var searching by mutableStateOf(false)
    fun previewCopy() = MemoLibraryState(LazyListState(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)).also {
        it.search = search
        it.searching = searching
    }
}
private object MemoNavigation {
    fun state(model: AppModel) = model.memoNavigationState ?: MemoLibraryState().also {model.memoNavigationState=it}
}

/** The preview has its own list state; only a completed return adopts its revealed row position. */
internal class MemoReturnTarget(val categoryId: String) {
    private val attached = CompletableDeferred<Unit>()
    private var revealAction: (suspend () -> Rect?)? = null
    private var commitAction: (() -> Unit)? = null
    internal var rowBounds by mutableStateOf<Rect?>(null)
    internal var fullRowHeight by mutableIntStateOf(0)
    internal fun attach(reveal: suspend () -> Rect?, commit: () -> Unit) {
        revealAction = reveal
        commitAction = commit
        attached.complete(Unit)
    }
    suspend fun reveal(): Rect? = withTimeoutOrNull(1500) {
        attached.await()
        revealAction?.invoke()
    }
    fun commitPosition() { commitAction?.invoke() }
}

private fun matchesMemo(category: RefCategory, search: String) = category.title.contains(search, true) ||
    category.group.contains(search, true) || category.rows.any {
        it.term.contains(search, true) || it.description.contains(search, true) || it.extra.contains(search, true)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ResourceLibraryScreen(model: AppModel, content: Content, returnTarget: MemoReturnTarget? = null) {
    val savedState = remember(model) { MemoNavigation.state(model) }
    val state = remember(model, returnTarget) {
        if (returnTarget == null) savedState else savedState.previewCopy().also { preview ->
            // A directly opened fiche may not belong to the saved filter; never target another row.
            content.references.firstOrNull { it.id == returnTarget.categoryId }?.let { category ->
                if (!matchesMemo(category, preview.search)) preview.search = ""
            }
        }
    }
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }
    var keyboardWasVisible by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val compactSearch=keyboardVisible&&questionToolsHorizontal()&&state.searching
    LaunchedEffect(focusRequested, state.searching) {
        if (focusRequested && state.searching && returnTarget == null) {
            withFrameNanos { }
            searchFocus.requestFocus()
            keyboard?.show()
            focusRequested = false
        }
    }
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) keyboardWasVisible = true
        else if (keyboardWasVisible) {
            keyboardWasVisible = false
            if (returnTarget == null && state.searching) {
                focus.clearFocus()
                if (state.search.isBlank()) state.searching = false
            }
        }
    }
    val overlap = LocalNavigationContentOverlap.current
    val toolsClearance=learningToolsBottomClearance(overlap)
    val tourClearance=tourContentClearance(model)
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    var headerBounds by remember { mutableStateOf<Rect?>(null) }
    val categories = remember(content, state.search) {
        content.references.filter { matchesMemo(it, state.search) }.groupBy { it.group.ifBlank { "Repères radio" } }
    }
    DisposableEffect(returnTarget, state, categories, toolsClearance) {
        returnTarget?.let { target ->
            var index = 1 // Sticky header, then one heading per family and its actual filtered rows.
            var targetIndex = -1
            categories.values.forEach { fiches ->
                index++
                fiches.forEach { category ->
                    if (category.id == target.categoryId) targetIndex = index
                    index++
                }
            }
            fun visibleTarget(): Rect? {
                val row = target.rowBounds ?: return null
                val viewport = viewportBounds ?: return null
                val header = headerBounds ?: return null
                val bottomClearance = with(density) { toolsClearance.toPx() }
                val stillLaidOut = state.list.layoutInfo.visibleItemsInfo.any { it.key == target.categoryId }
                return row.takeIf {
                    stillLaidOut && row.height >= target.fullRowHeight - 1f && row.top >= header.bottom - 1f &&
                        row.bottom <= viewport.bottom - bottomClearance + 1f
                }
            }
            target.attach(reveal = {
                if (targetIndex < 0) null else {
                    snapshotFlow { headerBounds?.takeIf { viewportBounds != null } }.filterNotNull().first()
                    visibleTarget() ?: run {
                        val headerHeight = requireNotNull(headerBounds).height
                        state.list.scrollToItem(targetIndex, -(headerHeight + with(density) { 8.dp.toPx() }).roundToInt())
                        snapshotFlow { visibleTarget() }.filterNotNull().first()
                    }
                }
            }, commit = {
                savedState.search = state.search
                savedState.searching = state.searching
                savedState.list.requestScrollToItem(state.list.firstVisibleItemIndex, state.list.firstVisibleItemScrollOffset)
            })
        }
        onDispose {}
    }
    LearningToolsLayout(model to "memo-library",enabled=returnTarget==null,toolsState=LearningToolsNavigation.library(model)) {bottomClearance->
        val horizontalTools=questionToolsHorizontal()
        val width=LocalConfiguration.current.screenWidthDp.dp
        val narrowToolRow=horizontalTools&&width<380.dp
        val footerBottom=if(narrowToolRow)bottomClearance else 16.dp+overlap
        val footerHeight=if(narrowToolRow)0.dp else bottomClearance-footerBottom
        val linkWidth=if(narrowToolRow)280.dp else (width-if(horizontalTools)304.dp else 172.dp).coerceIn(120.dp,280.dp)
        LazyColumn(Modifier.fillMaxSize().testTag("memo-library-list").onGloballyPositioned { viewportBounds = it.boundsInWindow() }, state = state.list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = footerBottom+tourClearance), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stickyHeader {
                Column(Modifier.fillMaxWidth().testTag("memo-library-header").stickyHeaderShadow(state.list).background(Cream).padding(top=if(compactSearch)16.dp else 0.dp,end=if(compactSearch)144.dp else 0.dp,bottom = 10.dp).onGloballyPositioned { headerBounds = it.boundsInWindow() }) {
                    if(!compactSearch) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mémo", Modifier.weight(1f).testTag("memo-library-title"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        IconButton(feedbackClick {
                            state.searching = !state.searching
                            if (state.searching) focusRequested = true
                            else { state.search = ""; focus.clearFocus(); keyboard?.hide() }
                        }) {
                            Icon(if (state.searching) Icons.Rounded.Close else Icons.Rounded.Search, if (state.searching) "Fermer la recherche des mémos" else "Rechercher un mémo")
                        }
                    }
                    }
                    if (state.searching) OutlinedTextField(state.search, { state.search = it }, label = { Text("Rechercher un mémo") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon=if(compactSearch)({IconButton(feedbackClick {focus.clearFocus();keyboard?.hide()}){Icon(Icons.Rounded.KeyboardArrowDown,"Masquer le clavier")}})else null,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus).testTag("memo-search-input"), singleLine = true)
                }
            }
            categories.forEach { (group, fiches) ->
                item(key = "group-$group") { Text(group, Modifier.padding(top = 10.dp, bottom = 2.dp), color = Teal, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                items(fiches, key = { it.id }) { category ->
                    Surface(onClick=feedbackClick { if (returnTarget == null) {
                        focus.clearFocus(); keyboard?.hide()
                        if (state.search.isBlank()) state.searching = false
                        model.resource = category
                    } }, modifier = Modifier.testTag("memo-row-${category.id}").onGloballyPositioned {
                        if (returnTarget?.categoryId == category.id) {
                            returnTarget.rowBounds = it.boundsInWindow()
                            returnTarget.fullRowHeight = it.size.height
                        }
                    }, color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        MemoMorphRow(category)
                    }
                }
            }
            if (categories.isEmpty()) item { Text("Aucun mémo trouvé. Essaie un autre mot.", color = Muted, fontSize = 14.sp) }
            item {
                // The source follows the last memo; the remaining footer clears the tools.
                Box(Modifier.fillMaxWidth().heightIn(min=footerHeight),contentAlignment=Alignment.TopCenter) {
                    CompleteCourseLink(maxLinkWidth=linkWidth)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ReferenceDetailScreen(model: AppModel, cat: RefCategory) {
    val listState=rememberSaveable(cat.id,saver=LazyListState.Saver) {LazyListState()}
    var search by rememberSaveable(cat.id) { mutableStateOf("") }
    var searching by rememberSaveable(cat.id) { mutableStateOf(false) }
    var region by rememberSaveable(cat.id) { mutableStateOf("1") }
    var openedTool by rememberSaveable(cat.id) { mutableStateOf<String?>(null) }
    // Keep form registries outside lazy items: save/restore must also retain tools scrolled off screen.
    val toolStates=rememberSaveableStateHolder()
    val audio = rememberReferenceAudio()
    val rows = remember(cat, search, region) {
        (if (cat.id == "bands") bandRowsForRegion(cat.rows, region) else cat.rows).filter {
            it.kind != "flashcard-only" && (it.term.contains(search, true) || it.description.contains(search, true) || it.extra.contains(search, true) || it.group.contains(search, true))
        }.groupBy { it.group }
    }
    LearningToolsLayout(model to cat.id,toolsState=LearningToolsNavigation.fiche(model,cat.id)) {bottomClearance->
        // Links and Pico already separate the last learning entry from the floating tools.
        // Only a short, narrow window needs the full reserve: its two tools sit beside Pico.
        val footerClearance = if (questionToolsHorizontal() && LocalConfiguration.current.screenWidthDp < 380)
            bottomClearance else 32.dp + LocalNavigationContentOverlap.current
        LazyColumn(Modifier.fillMaxSize().testTag("memo-detail-list"), state=listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = footerClearance), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stickyHeader {
                Column(Modifier.fillMaxWidth().testTag("memo-detail-header").stickyHeaderShadow(listState).background(Cream).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(feedbackClick { model.resource=null }, Modifier.size(40.dp)) { Icon(Icons.Rounded.ArrowBack, if(model.lesson!=null)"Retour au cours" else "Retour aux mémos") }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(cat.title, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                            if (cat.subtitle.isNotBlank()) Text(cat.subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
                        }
                        IconButton(feedbackClick { searching = !searching; if (!searching) search = "" }, Modifier.size(40.dp)) {
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
            if (hasReferenceTools(cat.id)) item(key = "interactive-tools") { ResourceInteractiveTools(cat.id,openedTool,{openedTool=it},toolStates) }
            if (cat.id == "itu-regions") item { ItuRegionsMap() }
            rows.forEach { (group, entries) ->
                if (group.isNotBlank()) item(key = "section-$group") { Text(group, Modifier.padding(top = 12.dp, bottom = 3.dp), color = Teal, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                items(entries, key = { it.term }) { row -> ReferenceEntry(cat, row, audio) }
            }
            if (rows.isEmpty()) item { Text("Aucun repère trouvé dans cette fiche.", color = Muted, fontSize = 14.sp) }
            item { CompleteCourseLink(cat.source,model.content?.courseSources?.get(cat.id).orEmpty()) }
            item { MemoFeedbackButton(cat) }
            item {
                Box(Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp),contentAlignment=Alignment.Center) {
                    Pico(Modifier.size(84.dp).testTag("memo-footer-pico"),mood=if(audio.preparing)MascotMood.THINKING else MascotMood.HAPPY,pose=MascotPose.WAVE)
                }
            }
        }
    }
}

@Composable private fun ReferenceReviewButtons(model: AppModel, cat: RefCategory) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(feedbackClick {
            val cards = model.content?.flashcards.orEmpty().filter { it.topic == cat.id }
            val reviews = model.progress.reviews
            val now = System.currentTimeMillis()
            val selected = cards.sortedWith(compareBy<Question> { (reviews[it.id]?.due ?: 0L) > now }.thenBy { reviews[it.id]?.due ?: 0L }).take(12)
            if (selected.isNotEmpty()) model.startQuestions(cat.title, selected)
        }, Modifier.weight(1.1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Réviser avec les flashcards", fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        }
        OutlinedButton(feedbackClick {
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
                if (speak != null) IconButton(speak, Modifier.size(44.dp).semantics {contentDescription=(if(cat.id=="nato"&&audio.activeWord==row.description)"Arrêter " else "Écouter ")+row.term}) {
                    if(cat.id=="nato"&&audio.activeWord==row.description&&audio.preparing)CircularProgressIndicator(Modifier.size(20.dp),color=Teal,strokeWidth=2.dp)
                    else Icon(if(cat.id=="nato"&&audio.activeWord==row.description)Icons.Rounded.Stop else Icons.Rounded.VolumeUp,null)
                }
                if (cat.id == "callsigns") radioCallsignMapQuery(row)?.let { location ->
                    IconButton(feedbackClick { openLink(context, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(location)) }, Modifier.size(44.dp)) { Icon(Icons.Rounded.LocationOn, "Situer " + location, tint = Teal) }
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
    if (speak != null) Surface(onClick = feedbackClick(speak), color = background, shape = RoundedCornerShape(16.dp)) { body() }
    else Surface(color = background, shape = RoundedCornerShape(16.dp)) { body() }
}

@Composable private fun CompleteCourseLink(source: String = "", courseSource:String = "",maxLinkWidth:Dp=280.dp) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val target=courseSource.ifBlank {courseUrl}
        if (source.isNotBlank() && source != target) TextButton(tourNavigation { openLink(context, source) }, Modifier.widthIn(max = maxLinkWidth), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Source de cette fiche", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center) }
        TextButton(tourNavigation { openLink(context,target) }, Modifier.widthIn(max = maxLinkWidth).testTag("memo-course-link"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(if(courseSource.isBlank())"Consulter le cours complet F6KGL/F5KFF" else "Consulter ce chapitre du cours F6KGL/F5KFF", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center) }
    }
}


@Composable internal fun MemoMorphRow(category: RefCategory, modifier: Modifier = Modifier, titleSize: androidx.compose.ui.unit.TextUnit = 16.sp) {
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = when (category.id) {
            "morse", "morse-rhythm" -> Icons.Rounded.GraphicEq
            "resistors", "formulas", "decibels", "units" -> Icons.Rounded.Science
            "bands", "propagation", "antennas" -> Icons.Rounded.SettingsInputAntenna
            "itu-regions", "callsigns" -> Icons.Rounded.Public
            else -> Icons.Rounded.Style
        }
        Box(Modifier.size(40.dp).background(Mist, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(category.title, fontSize = titleSize, fontWeight = FontWeight.Bold)
            Text("${category.rows.count { it.kind != "flashcard-only" }} repères" + (if (category.flashcards) " · flashcards" else "") + (if (hasReferenceTools(category.id)) " · outils" else ""), fontSize = 11.sp, color = Muted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}
