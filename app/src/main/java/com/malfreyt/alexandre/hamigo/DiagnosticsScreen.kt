package com.malfreyt.alexandre.hamigo

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DiagnosticSelection(val questions:List<Question>,initialPage:Int) {
    var currentPage by mutableIntStateOf(initialPage)
}

@Composable internal fun DiagnosticHud(owner:AppModel,shown:AppModel,modifier:Modifier=Modifier) {
    if(!LocalDiagnosticHudVisible.current)return
    val state=owner.diagnostics
    if(!state.enabled&&!state.frozen&&state.sandbox==null)return
    val q=shown.session?.current
    var inspectJson by remember(q?.id,q?.presentationSeed){mutableStateOf(false)}
    if(inspectJson&&q!=null)DiagnosticQuestionJson(q){inspectJson=false}
    Row(modifier.widthIn(max=360.dp).testTag("diagnostic-hud").background(Ink.copy(alpha=.08f),RoundedCornerShape(10.dp)).padding(end=7.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton({owner.debugToolsOpen=true},Modifier.size(32.dp).testTag("diagnostic-open")) {Icon(Icons.Rounded.BugReport,"Ouvrir les diagnostics",Modifier.size(18.dp),tint=Teal)}
        Column(Modifier.weight(1f,fill=false)) {
            Text(when {state.sandbox!=null->"BAC À SABLE · SYNCHRO COUPÉE";state.frozen->"TEST · PROGRESSION GELÉE";else->"DEBUG"},fontSize=9.sp,lineHeight=11.sp,color=Teal,fontWeight=FontWeight.Bold)
            if(state.enabled&&q!=null)Text("${q.id} · ${q.kind} · ${shown.session!!.index+1}/${shown.session!!.questions.size}",fontSize=9.sp,lineHeight=12.sp,fontFamily=FontFamily.Monospace,color=Ink,maxLines=2)
            if(state.enabled)Text(BuildConfig.VERSION_NAME,fontSize=8.sp,lineHeight=10.sp,color=Muted)
        }
        if(state.enabled&&q!=null) {
            IconButton({inspectJson=true},Modifier.size(48.dp).testTag("diagnostic-session-json")){Icon(Icons.Rounded.Code,"Inspecter le JSON de la question",Modifier.size(19.dp),tint=Teal)}
            if(shown.diagnosticModel)IconButton({DiagnosticData.testQuestion(shown,q)},Modifier.size(48.dp).testTag("diagnostic-regenerate")) {
                Icon(Icons.Rounded.Refresh,"Générer une nouvelle variante",Modifier.size(19.dp),tint=Teal)
            }
        }
    }
}

/** Native mock screens remain scrollable, while clicks and accessibility actions cannot escape. */
@Composable internal fun ReadOnlyDiagnosticPreview(content:@Composable ()->Unit) {
    Box(Modifier.fillMaxSize().clearAndSetSemantics {contentDescription="Aperçu de démonstration, défilement uniquement"}
        .pointerInput(Unit) {awaitPointerEventScope {while(true) {
            val event=awaitPointerEvent(PointerEventPass.Initial)
            event.changes.filter {it.changedToUpIgnoreConsumed()}.forEach {it.consume()}
        }}},contentAlignment=Alignment.TopStart) {content()}
}

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable internal fun DiagnosticsScreen(owner:AppModel) {
    val content=owner.content ?: return
    val context=LocalContext.current
    val courseLocations=remember(content){DiagnosticData.courseLocations(content)}
    val uiState=owner.diagnosticUiState
    val inspector=remember(owner,content){uiState.inspector ?: AppModel().apply {
        initializeDiagnostics(context,owner,content);DiagnosticData.copyLearningNames(this,owner)
    }.also {uiState.inspector=it}}
    DisposableEffect(inspector){onDispose {if(!owner.debugToolsOpen)uiState.disposeInspector()}}
    var tab by uiState::tab
    var selected by uiState::selected
    var selectedCourse by uiState::selectedCourse
    val catalogState=uiState.catalog
    val catalogListState=uiState.catalogList
    val skillsState=uiState.skills
    val skillsListState=uiState.skillsList
    var previewQuery by uiState::previewQuery
    var previewCategory by uiState::previewCategory
    val previewListState=uiState.previewList
    var questionReturnTab by uiState::questionReturnTab
    var courseQuery by uiState::courseQuery
    var courseFilter by uiState::courseFilter
    val courseListState=uiState.courseList
    // The speech audit is expensive and is only useful on Intros. Cancel it between
    // lessons when leaving that tab; keep a finished audit for this dialog's lifetime.
    val courseEntries by produceState<List<DiagnosticCourseEntry>?>(null,content,tab=="Intros") {
        if(value==null&&tab=="Intros")value=withContext(Dispatchers.Default) {
            val auditContext=currentCoroutineContext()
            NarrationAudit.entries(content.chapters){auditContext.ensureActive()}
        }
    }
    var mock by uiState::mock
    var previewBack by remember {mutableStateOf<(() -> Boolean)?>(null)}
    if(mock=="Onboarding") {DiagnosticOnboarding(owner,content){mock=null};return}
    if(mock=="Guide Hamigo") {DiagnosticHamigoGuide(owner,content){mock=null};return}
    fun back(){if(mock!=null&&previewBack?.invoke()==true)return;when {inspector.session!=null->inspector.leaveSession();inspector.resource!=null->inspector.resource=null;inspector.lesson!=null->inspector.lesson=null;selectedCourse!=null->selectedCourse=null;selected!=null->selected=null;mock!=null->mock=null;tab=="Questions"&&questionReturnTab!=null->{tab=questionReturnTab!!;questionReturnTab=null};else->owner.debugToolsOpen=false}}
    Dialog(onDismissRequest=::back,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize().testTag("diagnostics-screen"),color=Cream) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(if(inspector.session?.done==false)WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal) else WindowInsets.safeDrawing)) {
                if(inspector.session==null)Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(::back){Icon(if(inspector.session!=null||inspector.lesson!=null||inspector.resource!=null||selectedCourse!=null||selected!=null||mock!=null||questionReturnTab!=null)Icons.Rounded.ArrowBack else Icons.Rounded.Close,"Retour des diagnostics")}
                    Column(Modifier.weight(1f)){Text("Diagnostics",fontWeight=FontWeight.ExtraBold,fontSize=19.sp,lineHeight=22.sp);Text("${content.allQuestions.size} questions · ${BuildConfig.VERSION_NAME}",fontSize=10.sp,lineHeight=12.sp,color=Muted,maxLines=2)}
                    Icon(Icons.Rounded.BugReport,null,tint=Teal,modifier=Modifier.padding(end=6.dp))
                }
                if(inspector.session==null&&inspector.lesson==null&&inspector.resource==null&&selectedCourse==null&&selected==null&&mock==null) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf("Outils","Types","Questions","Compétences","Intros","TTS","Aperçus","Infos").forEach {label->FilterChip(tab==label,{tab=label;questionReturnTab=null},label={Text(label,fontSize=12.sp)},modifier=Modifier.testTag("diagnostic-tab-$label"))}
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        inspector.session!=null->FeedbackPreferenceScope(owner.interactionFeedback,inspector.progress.prefs) {
                            // The real session underneath stays covered; this visible test is not.
                            CompositionLocalProvider(LocalDiagnosticsCovered provides false,LocalContext provides inspector.diagnosticContext,LocalAnimatedBack provides {inspector.leaveSession()},LocalNavigationContentOverlap provides 0.dp) {QuizScreen(inspector)}
                        }
                        inspector.lesson!=null||inspector.resource!=null->FeedbackPreferenceScope(owner.interactionFeedback,inspector.progress.prefs) {
                            CompositionLocalProvider(LocalContext provides inspector.diagnosticContext,LocalDiagnosticsCovered provides false) {
                                HamigoAppContent(inspector,owner,diagnosticPreview=true)
                            }
                        }
                        selectedCourse!=null->CompositionLocalProvider(LocalContext provides inspector.diagnosticContext) {
                            DiagnosticCourseDetail(selectedCourse!!,uiState.courseDetails.getOrPut(selectedCourse!!.lesson.id){androidx.compose.foundation.lazy.LazyListState()},{inspector.previewLesson(selectedCourse!!.lesson)},
                                {inspector.startQuestions("Test · ${selectedCourse!!.lesson.id}",selectedCourse!!.lesson.questions)})
                        }
                        tab=="Intros"->DiagnosticCourseCatalog(courseEntries.orEmpty(),courseEntries==null,courseQuery,{courseQuery=it},courseFilter,{courseFilter=it},courseListState) {selectedCourse=it}
                        tab=="TTS"->CompositionLocalProvider(LocalContext provides inspector.diagnosticContext) {DiagnosticTtsLab(uiState)}
                        selected!=null->DiagnosticQuestionPager(selected!!,content,courseLocations,inspector,uiState) {q->DiagnosticData.testQuestion(inspector,q)}
                        mock!=null->DiagnosticMock(owner,content,mock!!){previewBack=it}
                        tab=="Questions"->DiagnosticQuestionCatalog(content,courseLocations,catalogState,catalogListState) {q,matches->selected=DiagnosticSelection(matches,matches.indexOfFirst {it.id==q.id})}
                        tab=="Compétences"->DiagnosticSkills(owner,content,skillsState,skillsListState) {skill->
                            catalogState.skill=skill;catalogState.query="";catalogState.kind="";catalogState.bank="";catalogState.variant=""
                            questionReturnTab="Compétences";tab="Questions"
                        }
                        tab=="Types"->DiagnosticTypeCatalog(content,uiState.types,{DiagnosticData.testQuestion(inspector,it)},
                            {inspector.startQuestions("Tour des types",it)}) {kind,variant->
                            catalogState.kind=kind;catalogState.variant=variant;catalogState.query="";catalogState.bank="";catalogState.skill=""
                            questionReturnTab="Types";tab="Questions"
                        }
                        tab=="Aperçus"->DiagnosticPreviews(previewQuery,{previewQuery=it},previewCategory,{previewCategory=it},previewListState) {mock=it}
                        tab=="Infos"->DiagnosticStats(content,uiState.statsList)
                        else->DiagnosticControls(owner,content,uiState) {
                            val personalized=content.courseQuestions.filter {it.template.isNotBlank()&&content.isReviewEligible(it)}
                            if(personalized.isNotEmpty())inspector.startQuestions("Questions personnalisées",personalized)
                        }
                    }
                    if(inspector.session!=null)DiagnosticHud(owner,inspector,Modifier.align(Alignment.TopStart).padding(start=8.dp,top=82.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DiagnosticControls(owner:AppModel,content:Content,uiState:DiagnosticUiState,testPersonalized:()->Unit) {
    val state=owner.diagnostics
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var xp by uiState::xp;var today by uiState::today;var days by uiState::days;var lessons by uiState::lessons
    var editLessons by remember {mutableStateOf(false)}
    val sandbox=state.sandbox
    if(editLessons&&sandbox!=null)DiagnosticLessonEditor(sandbox,content){editLessons=false}
    LazyColumn(Modifier.testTag("diagnostic-controls"),state=uiState.controlsList,contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        item {DiagnosticPanel {
            Text("Questions personnalisées",fontWeight=FontWeight.Bold)
            Text("Ton pseudo et ceux de ton équipe, dans une séance isolée. Aucun lien de compte ni identifiant de sauvegarde n’est copié.",fontSize=12.sp,lineHeight=17.sp,color=Muted)
            Button(testPersonalized,Modifier.fillMaxWidth().testTag("diagnostic-personalized"),enabled=content.courseQuestions.any {it.template.isNotBlank()&&content.isReviewEligible(it)}) {
                Text("Tester les questions personnalisées")
            }
        }}
        item {DiagnosticPanel {
            DiagnosticSwitch("Mode debug persistant","Identifiants et version en surimpression.",state.enabled,state::enable,"diagnostic-enabled")
            DiagnosticSwitch("Geler la progression","Aucun XP, cours validé ou rappel SRS ajouté par les séances de test.",state.frozen,state::freeze,"diagnostic-frozen")
        }}
        item {DiagnosticPanel(color=Mist) {
            DiagnosticSwitch("Bac à sable client","Fausses données en mémoire, sans synchro ni accès GitHub. Réversible.",state.sandbox!=null,{if(it)owner.startClientSandbox() else owner.stopClientSandbox()},"diagnostic-sandbox")
            if(state.sandbox!=null) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {DiagnosticNumber("XP total",xp,{xp=it},Modifier.weight(1f));DiagnosticNumber("XP aujourd’hui",today,{today=it},Modifier.weight(1f))}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {DiagnosticNumber("Jours de série",days,{days=it},Modifier.weight(1f));DiagnosticNumber("Cours terminés",lessons,{lessons=it},Modifier.weight(1f))}
                Button({focus.clearFocus();keyboard?.hide();DiagnosticData.seed(state.sandbox!!,xp=xp.toIntOrNull() ?: 1240,todayXp=today.toIntOrNull() ?: 24,days=days.toIntOrNull() ?: 7,completed=lessons.toIntOrNull() ?: 8)},Modifier.fillMaxWidth()){Text("Appliquer les données factices")}
                OutlinedButton({focus.clearFocus();keyboard?.hide();editLessons=true},Modifier.fillMaxWidth().testTag("diagnostic-edit-lessons")){Text("Choisir les cours terminés")}
                OutlinedButton({owner.stopClientSandbox()},Modifier.fillMaxWidth().testTag("diagnostic-revert")){Text("Revenir aux données réelles")}
            }
            Text("Le bac à sable est temporaire. Les préférences debug et le gel persistent ; tes sauvegardes restent séparées.",fontSize=12.sp,color=Muted)
        }}
        item {DiagnosticPanel {
            Text("Essayer les vibrations",fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Cran" to FeedbackCue.SLIDER_TICK,"Repère" to FeedbackCue.SLIDER_MARK,"Butée" to FeedbackCue.SLIDER_EDGE,"Flip" to FeedbackCue.FLASH_FLIP,"Lien" to FeedbackCue.SNAP,"Point" to FeedbackCue.MORSE_DOT,"Trait" to FeedbackCue.MORSE_DASH).forEach {(name,cue)->OutlinedButton({owner.interactionFeedback.event(cue)}){Text(name,fontSize=12.sp)}}
            }
            Text("Respecte le réglage Vibrations de l’app et d’Android.",fontSize=12.sp,color=Muted)
        }}
        item {DiagnosticPanel {Text("Accès calculatrice",fontWeight=FontWeight.Bold);Text("${DiagnosticAccess.CODE} puis =",fontFamily=FontFamily.Monospace,color=Teal);Text("73 88, deux fois, puis l’année en cours.",fontSize=12.sp,color=Muted)}}
        item {Spacer(Modifier.height(18.dp))}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DiagnosticLessonEditor(model:AppModel,content:Content,dismiss:()->Unit) {
    val completed=remember(model.revision){model.progress.completed}
    ModalBottomSheet(onDismissRequest=dismiss,containerColor=Cream) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal=16.dp).testTag("diagnostic-lesson-editor")) {
            Text("Cours terminés fictivement",fontWeight=FontWeight.ExtraBold,fontSize=20.sp)
            Text("${completed.size}/${content.lessons.size} · sans XP ni changement réel",color=Muted,fontSize=12.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button({DiagnosticData.setCompleted(model,content.lessons.map {it.id}.toSet())},Modifier.weight(1f).testTag("diagnostic-complete-all")){Text("Tout terminer",fontSize=12.sp)}
                OutlinedButton({DiagnosticData.setCompleted(model,emptySet())},Modifier.weight(1f).testTag("diagnostic-complete-none")){Text("Tout décocher",fontSize=12.sp)}
            }
            LazyColumn(Modifier.weight(1f).testTag("diagnostic-lessons"),contentPadding=PaddingValues(bottom=20.dp)) {
                items(content.lessons,key={it.id}) {lesson->
                    Row(Modifier.fillMaxWidth().testTag("diagnostic-lesson-${lesson.id}")
                        .toggleable(lesson.id in completed,role=androidx.compose.ui.semantics.Role.Checkbox) {on->DiagnosticData.setCompleted(model,if(on)completed+lesson.id else completed-lesson.id)}
                        .padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(lesson.id in completed,null)
                        Column(Modifier.weight(1f)) {Text(lesson.title,fontSize=14.sp,fontWeight=FontWeight.Bold);Text(lesson.id,fontSize=10.sp,fontFamily=FontFamily.Monospace,color=Muted)}
                    }
                }
            }
            TextButton(dismiss,Modifier.align(Alignment.End).testTag("diagnostic-lesson-done")){Text("Terminé")}
        }
    }
}
@Composable private fun DiagnosticSwitch(title:String,subtitle:String,checked:Boolean,change:(Boolean)->Unit,tag:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold,fontSize=14.sp);Text(subtitle,fontSize=12.sp,lineHeight=16.sp,color=Muted)}
        Switch(checked,change,Modifier.testTag(tag))
    }
}
@Composable private fun DiagnosticNumber(label:String,value:String,change:(String)->Unit,modifier:Modifier) {
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    OutlinedTextField(value,{change(it.filter(Char::isDigit).take(7))},modifier=modifier.testTag("diagnostic-value-$label"),label={Text(label,fontSize=11.sp,lineHeight=14.sp)},singleLine=true,
        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number,imeAction=androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}))
}

@Composable private fun DiagnosticQuestionPager(selection:DiagnosticSelection,content:Content,courseLocations:Map<String,List<String>>,inspector:AppModel,uiState:DiagnosticUiState,test:(Question)->Unit) {
    val pager=rememberPagerState(initialPage=selection.currentPage,pageCount={selection.questions.size})
    LaunchedEffect(pager.currentPage){selection.currentPage=pager.currentPage}
    val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton({scope.launch {pager.animateScrollToPage(pager.currentPage-1)}},enabled=pager.currentPage>0&&!pager.isScrollInProgress) {Icon(Icons.Rounded.ChevronLeft,"Question précédente")}
            Text("${pager.currentPage+1} / ${selection.questions.size}",Modifier.weight(1f).testTag("diagnostic-question-position"),color=Muted,fontSize=12.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            IconButton({scope.launch {pager.animateScrollToPage(pager.currentPage+1)}},enabled=pager.currentPage<selection.questions.lastIndex&&!pager.isScrollInProgress) {Icon(Icons.Rounded.ChevronRight,"Question suivante")}
        }
        HorizontalPager(pager,Modifier.weight(1f).fillMaxWidth().testTag("diagnostic-question-pager"),key={selection.questions[it].id},verticalAlignment=Alignment.Top) {page->
            val source=selection.questions[page]
            val q=remember(source,inspector){DiagnosticData.resolvedQuestion(inspector,source)}
            DiagnosticQuestionDetail(q,content,courseLocations,uiState.questionDetails.getOrPut(source.id){DiagnosticQuestionDetailState()},active=page==pager.currentPage) {test(q)}
        }
    }
}

@Composable private fun DiagnosticQuestionDetail(q:Question,content:Content,courseLocations:Map<String,List<String>>,state:DiagnosticQuestionDetailState,active:Boolean,test:()->Unit) {
    var reveal by state::reveal
    var inspectSpeech by state::inspectSpeech
    var inspectJson by remember(q.id){mutableStateOf(false)}
    var enlarged by remember(q.id){mutableStateOf(false)}
    val illustration=rememberExamArtwork(q.image)
    if(enlarged&&active)illustration?.let {FullscreenExamIllustration(it.original){enlarged=false}}
    if(inspectJson&&active)DiagnosticQuestionJson(q){inspectJson=false}
    LazyColumn(Modifier.fillMaxSize().then(if(active)Modifier.testTag("diagnostic-question-detail") else Modifier),state=state.list,contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        item {DiagnosticPanel {Text(q.id,Modifier.testTag("diagnostic-detail-${q.id}"),fontFamily=FontFamily.Monospace,color=Teal,fontWeight=FontWeight.Bold);Text("${diagnosticTypeName(q.kind)} · ${QuestionUiVariants.ids(q).joinToString {QuestionUiVariants.label(it)}}",fontSize=12.sp,color=Muted);MorseAwareText(q.prompt,fontWeight=FontWeight.Bold);illustration?.let {ExamIllustration(it,{enlarged=true})};if(q.visual.startsWith("sstv:"))SstvLearningVisual(q.visual)}}
        item {Action("Tester cette question",onClick=test)}
        item {OutlinedButton({inspectJson=true},Modifier.fillMaxWidth().testTag("diagnostic-detail-json")){Icon(Icons.Rounded.Code,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Inspecter le JSON")}}
        item {OutlinedButton({inspectSpeech=!inspectSpeech},Modifier.fillMaxWidth().testTag("diagnostic-question-narration")){Icon(Icons.Rounded.RecordVoiceOver,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(inspectSpeech)"Masquer le texte TTS" else "Inspecter le texte TTS")}}
        if(inspectSpeech&&active)item {DiagnosticNarrationPanel(remember(q.id){LessonNarration.segments(listOf(QuestionNarration.text(q)),4000)})}
        item {DiagnosticPanel {
            Text("${if(ContentFeedback.isExam1(q))"Question Exam1 · source originale" else if(q.kind=="flash")"Fiche Mémo · référence" else "Référence documentaire"} : ${q.source}",fontSize=12.sp,color=Muted)
            val locations=courseLocations[q.id].orEmpty()
            Text(if(locations.isEmpty())"Hors Parcours · ${DiagnosticData.origin(q)}" else locations.joinToString("\n"),fontSize=12.sp,lineHeight=17.sp,color=Teal)
            Text("Compétences : ${content.skillsFor(q).joinToString(", ").ifBlank {"aucune correspondance"}}",fontSize=12.sp,lineHeight=17.sp,color=Muted)
            if(q.value!=null)Text("Valeur : ${q.value} ${q.unit} · tolérance ${q.tolerance}",fontSize=12.sp,color=Muted)
            if(q.image!=null)Text("Asset : ${q.image}",fontFamily=FontFamily.Monospace,fontSize=11.sp,color=Muted)
            if(q.id in content.excluded)Text("Exclue de l’examen standard",color=Coral,fontSize=12.sp)
            TextButton({reveal=!reveal}){Text(if(reveal)"Masquer la correction" else "Voir les réponses et la correction")}
            if(reveal) {
                q.choices.forEachIndexed {i,choice->MorseAwareText("${i+1}. $choice",fontSize=13.sp)}
                MorseAwareText(solution(q),color=Teal,fontWeight=FontWeight.Bold)
                if(q.explanation.isNotBlank())MorseAwareText(q.explanation,fontSize=13.sp)
                q.pairs.forEach {Text("${it.left} ↔ ${it.right}",fontSize=13.sp)}
            }
        }}
    }
}

@Composable private fun DiagnosticOnboarding(owner:AppModel,content:Content,onClose:()->Unit) {
    val context=LocalContext.current
    val uiState=owner.diagnosticUiState
    val model=remember(owner,content){uiState.preview("Onboarding"){AppModel().apply {initializeDiagnostics(context,owner,content)}}}
    DisposableEffect(model){onDispose {if(!owner.debugToolsOpen||uiState.mock!="Onboarding")uiState.disposePreview()}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        FeedbackPreferenceScope(owner.interactionFeedback,model.progress.prefs) {
            CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false) {
                OnboardingScreen(model,simulation=true,onSimulationFinished=onClose)
            }
        }
    }
}

@Composable private fun DiagnosticMock(owner:AppModel,content:Content,preset:String,onBackChanged:((() -> Boolean)?)->Unit) {
    val context=LocalContext.current
    val uiState=owner.diagnosticUiState
    val model=remember(preset){uiState.preview(preset){AppModel().apply {
        initializeDiagnostics(context,owner,content)
        DiagnosticData.seed(this,when {preset.contains("début")->"empty";preset.contains("cassée")->"broken";else->"active"},
            todayXp=if(preset.contains("atteint"))DailyGoals.xp(60) else 24,completed=if(preset.startsWith("Parcours"))20 else 8)
        when {
            preset in DiagnosticStressPreviews.presets->DiagnosticStressPreviews.prepare(this,preset)
            preset.startsWith("Défis ·")||preset.startsWith("Historique ·")-> {
                DiagnosticPreviewCatalog.seedExams(this,when {preset.contains("trois")->3;preset.contains("deux")->2;else->0})
                route=if(preset.startsWith("Historique"))"exam-history" else "practice"
            }
            preset=="Paramètres"->route="settings"
            preset=="Mémo · bibliothèque"->route="resources"
            preset=="Mémo · fiche"->{route="resources";resource=content.references.firstOrNull()}
            preset.startsWith("Moi")->route="profile"
            preset.startsWith("Équipe")-> {route="friends";if(preset.contains("aucun"))friends=emptyList()}
            preset.startsWith("Parcours")->route="path"
            else->{
                route="practice"
                session=run {val exam=preset.contains("examen");val count=if(exam)40 else 8
                            val questions=List(count){Question("debug-$it","Quel appareil commande la transmission ?",listOf("Le manipulateur","Le fusible"),0,"Le manipulateur commande le signal.",section=if(exam&&it<20)"regulation" else "technique")}
                            Session(preset,questions.toMutableList(),lessonId=if(preset.startsWith("Fin de cours"))"debug-lesson" else null,exam=preset.contains("examen")).apply {
                                index=count;firstCorrect=if(exam)28 else if(preset.contains("consolider"))3 else 8;correct=firstCorrect;gain=0;regulationScore=15;techniqueScore=13
                                if(exam)questions.forEachIndexed {i,q->val good=if(i<20)i<15 else i<33;responses[i]=SessionResponse(good,display=q.choices[if(good)0 else 1],choiceIndex=if(good)0 else 1);if(!good)missed[q.id]=q}
                                else if(firstCorrect<8){missed[questions[0].id]=questions[0];unresolved.add(questions[0].id)}
                            }}
            }
        }
    }}}
    val origin=uiState.previewOrigin!!
    DisposableEffect(model) {
        onBackChanged {
            if(model.route==origin.route&&model.session===origin.session&&model.lesson===origin.lesson&&model.resource===origin.resource)false
            else when {
                model.session!=null->{model.leaveSession();true}
                model.resource!=null->{model.resource=null;true}
                model.lesson!=null->{model.lesson=null;true}
                model.route=="settings"->{model.route="profile";true}
                model.route=="exam-history"->{model.route="practice";true}
                else->false
            }
        }
        onDispose {onBackChanged(null);if(!owner.debugToolsOpen||uiState.mock!=preset)uiState.disposePreview()}
    }
    Column(Modifier.fillMaxSize().testTag("diagnostic-interactive-preview")) {
        Text("$preset · données fictives, sans connexion externe",Modifier.padding(horizontal=14.dp,vertical=8.dp),fontSize=12.sp,color=Teal)
        FeedbackPreferenceScope(owner.interactionFeedback,model.progress.prefs) {
            CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false) {
                HamigoAppContent(model,owner,diagnosticPreview=true)
            }
        }
    }
}

@Composable private fun DiagnosticStats(content:Content,listState:androidx.compose.foundation.lazy.LazyListState) {
    val density=LocalDensity.current;val window=LocalWindowInfo.current.containerSize
    val runtime=Runtime.getRuntime()
    LazyColumn(state=listState,contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        item {DiagnosticPanel {Text("Appareil et application",fontWeight=FontWeight.Bold);Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",fontSize=13.sp);Text(BuildConfig.VERSION_NAME,fontFamily=FontFamily.Monospace,fontSize=12.sp);Text("Fenêtre ${window.width} × ${window.height} px · densité ${density.density} · police ${density.fontScale}",fontSize=12.sp);Text("Heap utilisé : ${(runtime.totalMemory()-runtime.freeMemory())/1048576} Mio",fontSize=12.sp)}}
        item {DiagnosticPanel {Text("Contenu disponible",fontWeight=FontWeight.Bold);Text("${content.lessons.size} cours · ${content.references.size} fiches · ${content.allQuestions.size} IDs uniques");Text("${content.exam.size} Exam1 · ${content.excluded.size} exclusions · ${content.procedural.size} variantes · ${content.flashcards.size} flashcards",fontSize=12.sp,color=Muted)}}
        item {DiagnosticPanel {content.allQuestions.values.groupingBy {it.kind}.eachCount().toSortedMap().forEach {(type,count)->Row(Modifier.fillMaxWidth()){Text(diagnosticTypeName(type),Modifier.weight(1f),fontSize=13.sp);Text("$count",color=Teal,fontWeight=FontWeight.Bold)}}}}
    }
}
