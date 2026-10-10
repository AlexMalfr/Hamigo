package com.malfreyt.alexandre.hamigo

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable internal fun DiagnosticHud(owner:AppModel,shown:AppModel,modifier:Modifier=Modifier) {
    val state=owner.diagnostics
    if(!state.enabled&&!state.frozen&&state.sandbox==null)return
    Row(modifier.widthIn(max=320.dp).testTag("diagnostic-hud").background(Ink.copy(alpha=.08f),RoundedCornerShape(10.dp)).padding(end=7.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton({owner.debugToolsOpen=true},Modifier.size(32.dp).testTag("diagnostic-open")) {Icon(Icons.Rounded.BugReport,"Ouvrir les diagnostics",Modifier.size(18.dp),tint=Teal)}
        val q=shown.session?.current
        Column(Modifier.weight(1f,fill=false)) {
            Text(when {state.sandbox!=null->"BAC À SABLE · SYNCHRO COUPÉE";state.frozen->"TEST · PROGRESSION GELÉE";else->"DEBUG"},fontSize=9.sp,lineHeight=11.sp,color=Teal,fontWeight=FontWeight.Bold)
            if(state.enabled&&q!=null)Text("${q.id} · ${q.kind} · ${shown.session!!.index+1}/${shown.session!!.questions.size}",fontSize=9.sp,lineHeight=12.sp,fontFamily=FontFamily.Monospace,color=Ink,maxLines=2)
            if(state.enabled)Text(BuildConfig.VERSION_NAME,fontSize=8.sp,lineHeight=10.sp,color=Muted)
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
    val inspector=remember(owner,content){AppModel().apply {initializeDiagnostics(context,owner,content)}}
    DisposableEffect(inspector){onDispose {inspector.disposeDiagnostics()}}
    var tab by remember {mutableStateOf("Outils")}
    var selected by remember {mutableStateOf<Question?>(null)}
    var mock by remember {mutableStateOf<String?>(null)}
    fun back(){when {inspector.session!=null->inspector.leaveSession();selected!=null->selected=null;mock!=null->mock=null;else->owner.debugToolsOpen=false}}
    Dialog(onDismissRequest=::back,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize().testTag("diagnostics-screen"),color=Cream) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(if(inspector.session?.done==false)WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal) else WindowInsets.safeDrawing)) {
                if(inspector.session==null)Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(::back){Icon(if(inspector.session!=null||selected!=null||mock!=null)Icons.Rounded.ArrowBack else Icons.Rounded.Close,"Retour des diagnostics")}
                    Column(Modifier.weight(1f)){Text("Diagnostics",fontWeight=FontWeight.ExtraBold,fontSize=22.sp,lineHeight=26.sp);Text("${content.allQuestions.size} questions · ${BuildConfig.VERSION_NAME}",fontSize=11.sp,lineHeight=14.sp,color=Muted,maxLines=2)}
                    Icon(Icons.Rounded.BugReport,null,tint=Teal,modifier=Modifier.padding(end=12.dp))
                }
                if(inspector.session==null&&selected==null&&mock==null) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("Outils","Types","Questions","Aperçus","Infos").forEach {label->FilterChip(tab==label,{tab=label},label={Text(label,fontSize=12.sp)},modifier=Modifier.testTag("diagnostic-tab-$label"))}
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        inspector.session!=null->FeedbackPreferenceScope(owner.interactionFeedback,inspector.progress.prefs) {
                            // The real session underneath stays covered; this visible test is not.
                            CompositionLocalProvider(LocalDiagnosticsCovered provides false,LocalContext provides inspector.diagnosticContext,LocalAnimatedBack provides {inspector.leaveSession()},LocalNavigationContentOverlap provides 0.dp) {QuizScreen(inspector)}
                        }
                        selected!=null->DiagnosticQuestionDetail(selected!!,content,courseLocations) {inspector.startQuestions("Test · ${selected!!.id}",listOf(selected!!));selected=null}
                        mock!=null->DiagnosticMock(owner,content,mock!!)
                        tab=="Questions"->DiagnosticCatalog(content,courseLocations) {selected=it}
                        tab=="Types"->LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            item {Action("Tester tous les types"){inspector.startQuestions("Tour des types",DiagnosticData.representatives(content))}}
                            items(DiagnosticData.representatives(content),key={it.kind}) {q->Panel {
                                Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){Text(DiagnosticData.typeName(q.kind),fontWeight=FontWeight.Bold);Text(q.kind,fontFamily=FontFamily.Monospace,fontSize=11.sp,color=Muted)};TextButton({inspector.startQuestions("Test · ${q.kind}",listOf(q))}){Text("Tester")}}
                                Text(q.id,fontSize=10.sp,color=Muted,fontFamily=FontFamily.Monospace)
                            }}
                            item {Spacer(Modifier.height(18.dp))}
                        }
                        tab=="Aperçus"->LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            item {Text("Écrans natifs avec données factices. Tu peux défiler ; les actions sont bloquées.",fontSize=13.sp,color=Muted)}
                            items(listOf("Moi · début","Moi · série en cours","Moi · objectif atteint","Moi · série cassée","Équipe · deux amis","Parcours · chapitres terminés","Fin de cours · réussite","Fin de cours · à consolider","Fin d’examen")) {label->OutlinedButton({mock=label},Modifier.fillMaxWidth()){Text(label)}}
                        }
                        tab=="Infos"->DiagnosticStats(content)
                        else->DiagnosticControls(owner,content)
                    }
                    if(inspector.session!=null)DiagnosticHud(owner,inspector,Modifier.align(Alignment.TopStart).padding(start=8.dp,top=82.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DiagnosticControls(owner:AppModel,content:Content) {
    val state=owner.diagnostics
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var xp by remember {mutableStateOf("1240")};var today by remember {mutableStateOf("24")};var days by remember {mutableStateOf("7")};var lessons by remember {mutableStateOf("8")}
    var editLessons by remember {mutableStateOf(false)}
    val sandbox=state.sandbox
    if(editLessons&&sandbox!=null)DiagnosticLessonEditor(sandbox,content){editLessons=false}
    LazyColumn(Modifier.testTag("diagnostic-controls"),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Panel {
            DiagnosticSwitch("Mode debug persistant","Identifiants et version en surimpression.",state.enabled,state::enable,"diagnostic-enabled")
            DiagnosticSwitch("Geler la progression","Aucun XP, cours validé ou rappel SRS ajouté par les séances de test.",state.frozen,state::freeze,"diagnostic-frozen")
        }}
        item {Panel(color=Mist) {
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
        item {Panel {
            Text("Essayer les vibrations",fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Cran" to FeedbackCue.SLIDER_TICK,"Repère" to FeedbackCue.SLIDER_MARK,"Butée" to FeedbackCue.SLIDER_EDGE,"Flip" to FeedbackCue.FLASH_FLIP,"Lien" to FeedbackCue.SNAP,"Point" to FeedbackCue.MORSE_DOT,"Trait" to FeedbackCue.MORSE_DASH).forEach {(name,cue)->OutlinedButton({owner.interactionFeedback.event(cue)}){Text(name,fontSize=12.sp)}}
            }
            Text("Respecte le réglage Vibrations de l’app et d’Android.",fontSize=12.sp,color=Muted)
        }}
        item {Panel {Text("Accès calculatrice",fontWeight=FontWeight.Bold);Text("${DiagnosticAccess.CODE} puis =",fontFamily=FontFamily.Monospace,color=Teal);Text("73 88, deux fois, puis l’année en cours.",fontSize=12.sp,color=Muted)}}
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

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable private fun DiagnosticCatalog(content:Content,courseLocations:Map<String,List<String>>,choose:(Question)->Unit) {
    val all=remember(content){content.allQuestions.values.toList()}
    var query by remember {mutableStateOf("")};var kind by remember {mutableStateOf("")};var menu by remember {mutableStateOf(false)}
    var bank by remember {mutableStateOf("")}
    val counts=remember(all){all.groupingBy(DiagnosticData::origin).eachCount()}
    val matches by produceState(emptyList<Question>(),all,query,kind,bank) {delay(150);value=withContext(Dispatchers.Default){DiagnosticData.search(all,query,kind,bank,courseLocations)}}
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=14.dp)) {
        OutlinedTextField(query,{query=it},label={Text("Tous les champs")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("diagnostic-search"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("" to "Toutes", "Hamigo" to "Hamigo", "Exam1" to "Exam’1", "Variante" to "Variantes", "Mémo" to "Mémo").forEach {(value,label)->
                FilterChip(bank==value,{bank=value},label={Text("$label · ${if(value.isBlank())all.size else counts[value] ?: 0}",fontSize=11.sp)},modifier=Modifier.testTag("diagnostic-bank-${value.ifBlank {"all"}}"))
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box {TextButton({menu=true}){Text(if(kind.isBlank())"Tous les types" else DiagnosticData.typeName(kind));Icon(Icons.Rounded.ExpandMore,null)}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem({Text("Tous les types")},{kind="";menu=false})
                    all.map{it.kind}.distinct().sorted().forEach {k->DropdownMenuItem({Text(DiagnosticData.typeName(k))},{kind=k;menu=false})}
                }}
            Spacer(Modifier.weight(1f));Text("${matches.size} résultat${if(matches.size!=1)"s" else ""}",fontSize=12.sp,color=Muted)
        }
        LazyColumn(Modifier.weight(1f).testTag("diagnostic-catalog"),verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=18.dp)) {
            items(matches,key={it.id}) {q->Surface(onClick={choose(q)},modifier=Modifier.testTag("diagnostic-question-${q.id}"),shape=RoundedCornerShape(15.dp),color=Color.White) {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(q.id,fontFamily=FontFamily.Monospace,fontSize=12.sp,color=Teal,fontWeight=FontWeight.Bold)
                    MorseAwareText(q.prompt.take(180)+(if(q.prompt.length>180)"…" else ""),fontSize=14.sp,lineHeight=19.sp)
                    Text("${DiagnosticData.typeName(q.kind)} · ${q.topic} · ${DiagnosticData.origin(q)}${if(q.image!=null)" · image" else ""}",fontSize=10.sp,color=Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                    courseLocations[q.id]?.let {locations->Text(locations.joinToString("\n"),fontSize=11.sp,lineHeight=15.sp,color=Teal)}
                }
            }}
            if(matches.isEmpty())item {Text(if(bank=="Exam1"&&content.exam.isEmpty())"La banque Exam’1 n’est pas encore disponible. Son téléchargement se fait en arrière-plan ; son état est visible dans les paramètres." else "Aucune question trouvée.",Modifier.padding(vertical=12.dp),color=Muted)}
        }
    }
}

@Composable private fun DiagnosticQuestionDetail(q:Question,content:Content,courseLocations:Map<String,List<String>>,test:()->Unit) {
    var reveal by remember(q.id){mutableStateOf(false)}
    val illustration=rememberExamArtwork(q.image)
    LazyColumn(Modifier.testTag("diagnostic-question-detail"),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Panel {Text(q.id,fontFamily=FontFamily.Monospace,color=Teal,fontWeight=FontWeight.Bold);Text("${q.kind} · ${q.section} · ${q.topic}",fontSize=12.sp,color=Muted);MorseAwareText(q.prompt,fontWeight=FontWeight.Bold);illustration?.let {ExamIllustration(it,{})}}}
        item {Action("Tester cette question",onClick=test)}
        item {Panel {
            Text("Origine : ${q.source}",fontSize=12.sp,color=Muted)
            val locations=courseLocations[q.id].orEmpty()
            Text(if(locations.isEmpty())"Hors Parcours · ${DiagnosticData.origin(q)}" else locations.joinToString("\n"),fontSize=12.sp,lineHeight=17.sp,color=Teal)
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

@Composable private fun DiagnosticMock(owner:AppModel,content:Content,preset:String) {
    val context=LocalContext.current
    val model=remember(preset){AppModel().apply {
        initializeDiagnostics(context,owner,content)
        DiagnosticData.seed(this,when {preset.contains("début")->"empty";preset.contains("cassée")->"broken";else->"active"},
            todayXp=if(preset.contains("atteint"))75 else 24,completed=if(preset.startsWith("Parcours"))20 else 8)
    }}
    DisposableEffect(model){onDispose {model.disposeDiagnostics()}}
    Column(Modifier.fillMaxSize()) {
        Text("$preset · défilement uniquement",Modifier.padding(horizontal=14.dp,vertical=8.dp),fontSize=12.sp,color=Teal)
        CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalNavigationContentOverlap provides 0.dp) {
            ReadOnlyDiagnosticPreview {
                when {
                    preset.startsWith("Moi")->ProfileScreen(model,content)
                    preset.startsWith("Équipe")->FriendsScreen(model)
                    preset.startsWith("Parcours")->PathScreen(model,content)
                    else->{
                        val session=remember(preset) {val exam=preset.contains("examen");val count=if(exam)40 else 8
                            val questions=List(count){Question("debug-$it","Quel appareil commande la transmission ?",listOf("Le manipulateur","Le fusible"),0,"Le manipulateur commande le signal.",section=if(exam&&it<20)"regulation" else "technique")}
                            Session(preset,questions.toMutableList(),lessonId=if(preset.startsWith("Fin de cours"))"debug-lesson" else null,exam=preset.contains("examen")).apply {
                                index=count;firstCorrect=if(exam)28 else if(preset.contains("consolider"))3 else 8;correct=firstCorrect;gain=0;regulationScore=15;techniqueScore=13
                                if(exam)questions.forEachIndexed {i,q->val good=if(i<20)i<15 else i<33;responses[i]=SessionResponse(good,display=q.choices[if(good)0 else 1],choiceIndex=if(good)0 else 1);if(!good)missed[q.id]=q}
                                else if(firstCorrect<8){missed[questions[0].id]=questions[0];unresolved.add(questions[0].id)}
                            }}
                        ResultsScreen(model,session)
                    }
                }
            }
        }
    }
}

@Composable private fun DiagnosticStats(content:Content) {
    val density=LocalDensity.current;val window=LocalWindowInfo.current.containerSize
    val runtime=Runtime.getRuntime()
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Panel {Text("Appareil et application",fontWeight=FontWeight.Bold);Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",fontSize=13.sp);Text(BuildConfig.VERSION_NAME,fontFamily=FontFamily.Monospace,fontSize=12.sp);Text("Fenêtre ${window.width} × ${window.height} px · densité ${density.density} · police ${density.fontScale}",fontSize=12.sp);Text("Heap utilisé : ${(runtime.totalMemory()-runtime.freeMemory())/1048576} Mio",fontSize=12.sp)}}
        item {Panel {Text("Contenu embarqué",fontWeight=FontWeight.Bold);Text("${content.lessons.size} cours · ${content.references.size} fiches · ${content.allQuestions.size} IDs uniques");Text("${content.exam.size} Exam1 · ${content.excluded.size} exclusions · ${content.procedural.size} variantes · ${content.flashcards.size} flashcards",fontSize=12.sp,color=Muted)}}
        item {Panel {content.allQuestions.values.groupingBy {it.kind}.eachCount().toSortedMap().forEach {(type,count)->Row(Modifier.fillMaxWidth()){Text(DiagnosticData.typeName(type),Modifier.weight(1f),fontSize=13.sp);Text("$count",color=Teal,fontWeight=FontWeight.Bold)}}}}
    }
}
