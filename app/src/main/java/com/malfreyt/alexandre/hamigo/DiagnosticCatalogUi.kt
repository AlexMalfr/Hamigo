package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Owned above question details, including its cached result list, so return cannot clamp the scroll. */
internal class DiagnosticCatalogState {
    var query by mutableStateOf("")
    var kind by mutableStateOf("")
    var bank by mutableStateOf("")
    var variant by mutableStateOf("")
    var skill by mutableStateOf("")
    var appliedKey by mutableStateOf<String?>(null)
    var matches by mutableStateOf<List<Question>>(emptyList())
    var indexedContent:Content?=null
    var corpora=java.util.concurrent.ConcurrentHashMap<String,String>()
}

internal class DiagnosticTypeState {
    var query by mutableStateOf("")
    var expanded by mutableStateOf<Set<String>>(emptySet())
    val listState=LazyListState()
}

/** Screen-local tools state belongs to the ViewModel, never an Activity or a static cache. */
internal class DiagnosticUiState {
    var inspector:AppModel?=null
    private var previewModel:AppModel?=null
    private var previewPreset:String?=null
    var previewOrigin:DiagnosticPreviewOrigin?=null;private set
    fun preview(preset:String,create:()->AppModel):AppModel {
        if(previewPreset!=preset)disposePreview()
        return previewModel ?: create().also {previewModel=it;previewPreset=preset;previewOrigin=DiagnosticPreviewOrigin(it.route,it.session,it.lesson,it.resource)}
    }
    fun disposePreview(){previewModel?.disposeDiagnostics();previewModel=null;previewPreset=null;previewOrigin=null}
    fun disposeInspector() {
        disposePreview()
        inspector?.disposeDiagnostics();inspector=null
        selected=null;selectedCourse=null;mock=null;questionReturnTab=null
        questionDetails.clear();courseDetails.clear()
    }
    var tab by mutableStateOf("Outils")
    var selected by mutableStateOf<DiagnosticSelection?>(null)
    var selectedCourse by mutableStateOf<DiagnosticCourseEntry?>(null)
    val catalog=DiagnosticCatalogState()
    val catalogList=LazyListState()
    val types=DiagnosticTypeState()
    val skills=DiagnosticSkillsState()
    val skillsList=LazyListState()
    var previewQuery by mutableStateOf("")
    var previewCategory by mutableStateOf("")
    val previewList=LazyListState()
    var questionReturnTab by mutableStateOf<String?>(null)
    var courseQuery by mutableStateOf("")
    var courseFilter by mutableStateOf("")
    val courseList=LazyListState()
    val courseDetails=mutableMapOf<String,LazyListState>()
    val questionDetails=mutableMapOf<String,DiagnosticQuestionDetailState>()
    var mock by mutableStateOf<String?>(null)
    val controlsList=LazyListState()
    val statsList=LazyListState()
    val ttsList=LazyListState()
    var ttsText by mutableStateOf("E = ½LI²")
    var xp by mutableStateOf("1240")
    var today by mutableStateOf("24")
    var days by mutableStateOf("7")
    var lessons by mutableStateOf("8")
}

internal data class DiagnosticPreviewOrigin(val route:String,val session:Session?,val lesson:Lesson?,val resource:RefCategory?)

internal class DiagnosticQuestionDetailState {
    val list=LazyListState()
    var reveal by mutableStateOf(false)
    var inspectSpeech by mutableStateOf(false)
}

@Composable internal fun DiagnosticPanel(modifier:Modifier=Modifier,color:Color=Color.White,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),color=color) {
        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
    }
}

@Composable internal fun DiagnosticIconAction(description:String,icon:androidx.compose.ui.graphics.vector.ImageVector,
    modifier:Modifier=Modifier,onClick:()->Unit) {
    IconButton(onClick,modifier.size(48.dp)) {Icon(icon,description,Modifier.size(21.dp),tint=Teal)}
}

internal fun diagnosticTypeName(kind:String)=DiagnosticData.typeName(kind)

internal object DiagnosticUiExamples {
    val questions=listOf(
        Question("diagnostic-ui-rows-text","Retrouve le nom de chaque grandeur.",emptyList(),0,"La tension s’exprime en volts ; le courant en ampères.",kind="rows",source="Diagnostics · exemple fictif",
            rows=listOf(AnswerRow("voltage","Grandeur exprimée en volts",acceptedAnswers=listOf("tension","tension électrique")),AnswerRow("current","Grandeur exprimée en ampères",acceptedAnswers=listOf("courant","intensité","intensité du courant")))),
        Question("diagnostic-ui-rows-dropdown","Choisis l’appareil adapté à chaque grandeur.",emptyList(),0,"Le voltmètre mesure une tension et l’ampèremètre un courant.",kind="rows",source="Diagnostics · exemple fictif",
            rows=listOf(AnswerRow("voltage","Tension entre deux points",listOf("Voltmètre","Ampèremètre","Ohmmètre"),0),AnswerRow("current","Courant d’une branche",listOf("Voltmètre","Ampèremètre","Ohmmètre"),1))),
        Question("diagnostic-ui-rows-mixed","Complète les deux lignes de cette maquette.",emptyList(),0,"La tension se mesure en volts et un courant traverse l’ampèremètre en série.",kind="rows",source="Diagnostics · exemple fictif",
            rows=listOf(AnswerRow("unit","Unité de la tension",acceptedAnswers=listOf("volt","volts","V")),AnswerRow("connection","Branchement de l’ampèremètre",listOf("En parallèle","En série"),1))),
        Question("diagnostic-ui-text-long","Explique comment tu prépares une mesure de tension sur une maquette basse tension.",emptyList(),0,"Exemple fictif : mode tension, bornes, calibre puis mesure en parallèle. La réponse attendue sert à vérifier le champ développé, pas à évaluer toutes les formulations possibles.",kind="text",source="Diagnostics · exemple fictif",
            acceptedAnswers=listOf("Je vérifie le mode tension, les bornes et le calibre avant de brancher le voltmètre en parallèle.")),
        Question("diagnostic-ui-spoken-long","Maquette hors Parcours. Pour essayer le champ multiligne, dicte : Je vérifie le mode tension, les bornes et le calibre avant de brancher le voltmètre en parallèle.",emptyList(),0,"Cette phrase imposée sert uniquement à tester la saisie multiligne. Les cours ne demandent pas de réciter une explication rédigée mot pour mot.",kind="spoken",source="Diagnostics · exemple fictif",
            acceptedAnswers=listOf("Je vérifie le mode tension, les bornes et le calibre avant de brancher le voltmètre en parallèle."))
    )
    fun all(content:Content)=content.allQuestions.values.toList()+questions
}

private fun diagnosticQuestionCorpus(q:Question,content:Content,locations:Map<String,List<String>>)=
    NarrationAudit.fold((listOf(q.id,q.prompt,q.explanation,q.topic,q.section,q.kind,q.source,q.image.orEmpty(),q.unit,q.visual,
        q.value?.toString().orEmpty(),q.template,QuestionUiVariants.searchable(q))+q.choices+q.bands+q.acceptedAnswers+q.distractors+
        q.pairs.flatMap {listOf(it.left,it.right)}+q.rows.flatMap {listOf(it.id,it.prompt)+it.choices+it.acceptedAnswers}+
        content.skillsFor(q)+locations[q.id].orEmpty()).joinToString(" "))
internal fun diagnosticQuestionMatches(q:Question,query:String,content:Content,locations:Map<String,List<String>>):Boolean {
    if(query.isBlank())return true
    return NarrationAudit.fold(query).split(Regex("\\s+")).filter(String::isNotBlank).all(diagnosticQuestionCorpus(q,content,locations)::contains)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DiagnosticQuestionCatalog(content:Content,courseLocations:Map<String,List<String>>,state:DiagnosticCatalogState,
    listState:LazyListState,choose:(Question,List<Question>)->Unit) {
    val all=remember(content){DiagnosticUiExamples.all(content)}
    var menu by remember {mutableStateOf(false)}
    val counts=remember(all){all.groupingBy(DiagnosticData::origin).eachCount()}
    val key=listOf(state.query,state.kind,state.bank,state.variant,state.skill).joinToString("\u0000")
    LaunchedEffect(content,key) {
        if(state.indexedContent===content&&state.appliedKey==key)return@LaunchedEffect
        if(state.indexedContent!==content){state.indexedContent=content;state.corpora=java.util.concurrent.ConcurrentHashMap()}
        if(state.appliedKey!=key)listState.scrollToItem(0)
        delay(150)
        val skills=state.skill.takeIf(String::isNotBlank)?.let {SkillHierarchy.descendants(it).toSet()}
        val kind=state.kind;val bank=state.bank;val variant=state.variant;val query=state.query.trim()
        val words=NarrationAudit.fold(query).split(Regex("\\s+")).filter(String::isNotBlank)
        val corpusCache=state.corpora
        val exact=all.firstOrNull {it.id==query}
        state.matches=withContext(Dispatchers.Default){(exact?.let(::listOf) ?: all).filter {q->
            (kind.isBlank()||q.kind==kind)&&(bank.isBlank()||DiagnosticData.origin(q)==bank)&&
                (variant.isBlank()||QuestionUiVariants.matches(q,variant))&&
                (skills==null||content.skillsFor(q).any {it in skills})&&
                (exact!=null||words.isEmpty()||words.all(corpusCache.getOrPut(q.id){diagnosticQuestionCorpus(q,content,courseLocations)}::contains))
        }}
        state.appliedKey=key
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=8.dp)) {
        OutlinedTextField(state.query,{state.query=it},label={Text("Texte, ID, compétence ou interface")},leadingIcon={Icon(Icons.Rounded.Search,null)},
            trailingIcon=if(state.query.isNotEmpty())({IconButton({state.query=""}){Icon(Icons.Rounded.Close,"Effacer la recherche")}}) else null,
            singleLine=true,modifier=Modifier.fillMaxWidth().testTag("diagnostic-search"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("" to "Toutes","Hamigo" to "Hamigo","Exam1" to "Exam’1","Variante" to "Variantes","Mémo" to "Mémo","Exemple" to "Exemples").forEach {(value,label)->
                FilterChip(state.bank==value,{state.bank=value},label={Text("$label · ${if(value.isBlank())all.size else counts[value] ?: 0}",fontSize=11.sp)},modifier=Modifier.testTag("diagnostic-bank-${value.ifBlank {"all"}}"))
            }
        }
        if(state.skill.isNotBlank())InputChip(true,{state.skill=""},label={Text("Compétence : ${SkillHierarchy.family(state.skill)?.title ?: content.skills.firstOrNull {it.id==state.skill}?.title ?: state.skill}")},
            trailingIcon={Icon(Icons.Rounded.Close,"Retirer le filtre de compétence",Modifier.size(18.dp))},modifier=Modifier.testTag("diagnostic-skill-filter"))
        if(state.variant.isNotBlank())InputChip(true,{state.variant=""},label={Text(QuestionUiVariants.label(state.variant))},
            trailingIcon={Icon(Icons.Rounded.Close,"Retirer le filtre d’interface",Modifier.size(18.dp))},modifier=Modifier.testTag("diagnostic-interface-filter"))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box {TextButton({menu=true}){Text(if(state.kind.isBlank())"Tous les types" else diagnosticTypeName(state.kind));Icon(Icons.Rounded.ExpandMore,null)}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem({Text("Tous les types")},{state.kind="";menu=false})
                    all.map {it.kind}.distinct().sorted().forEach {kind->DropdownMenuItem({Text(diagnosticTypeName(kind))},{state.kind=kind;menu=false})}
                }}
            Spacer(Modifier.weight(1f));Text("${state.matches.size} résultat${if(state.matches.size!=1)"s" else ""}",fontSize=12.sp,color=Muted)
        }
        LazyColumn(Modifier.weight(1f).testTag("diagnostic-catalog"),state=listState,contentPadding=PaddingValues(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(state.matches,key={it.id}) {q->Surface(onClick={choose(q,state.matches)},shape=RoundedCornerShape(10.dp),color=Color.White,modifier=Modifier.testTag("diagnostic-question-${q.id}")) {
                Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(q.id,fontFamily=FontFamily.Monospace,fontSize=12.sp,color=Teal,fontWeight=FontWeight.Bold)
                    MorseAwareText(q.prompt.take(180)+(if(q.prompt.length>180)"…" else ""),fontSize=14.sp,lineHeight=19.sp)
                    Text("${diagnosticTypeName(q.kind)} · ${QuestionUiVariants.ids(q).joinToString {QuestionUiVariants.label(it)}}",fontSize=11.sp,lineHeight=15.sp,color=Muted)
                    if(q.id.startsWith("diagnostic-ui-"))Text("Exemple fictif · uniquement en Diagnostics",fontSize=11.sp,color=Teal)
                    courseLocations[q.id]?.let {Text(it.joinToString("\n"),fontSize=11.sp,lineHeight=15.sp,color=Teal)}
                }
            }}
            if(state.matches.isEmpty())item {Text(if(state.bank=="Exam1"&&content.exam.isEmpty())"La banque Exam’1 n’est pas encore téléchargée." else "Aucune question trouvée.",Modifier.padding(vertical=16.dp),color=Muted)}
        }
    }
}

@Composable internal fun DiagnosticTypeCatalog(content:Content,state:DiagnosticTypeState,test:(Question)->Unit,
    testAll:(List<Question>)->Unit,showQuestions:(String,String)->Unit) {
    val all=remember(content){DiagnosticUiExamples.all(content)}
    val groups=remember(all){all.groupBy {it.kind}.toSortedMap()}
    val words=remember(state.query){NarrationAudit.fold(state.query).split(Regex("\\s+")).filter(String::isNotBlank)}
    val rows=remember(groups,words){groups.mapNotNull {(kind,questions)->
        val primary=QuestionUiVariants.primary(questions)
        val typeText=NarrationAudit.fold("${diagnosticTypeName(kind)} $kind présentation courante ${QuestionUiVariants.searchable(primary)}")
        val variants=QuestionUiVariants.representatives(questions).filter {entry->val variant=entry.first;val q=entry.second;
            variant.id!="standard"&&QuestionUiVariants.compositionKey(primary)!=QuestionUiVariants.compositionKey(q)&&words.all {it in "$typeText ${NarrationAudit.fold("${variant.id} ${variant.title} ${variant.description}")}"}
        }
        if(words.all(typeText::contains)||variants.isNotEmpty())Triple(kind,questions,variants) else null
    }}
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=8.dp)) {
        OutlinedTextField(state.query,{state.query=it},label={Text("Type ou variante d’interface")},singleLine=true,leadingIcon={Icon(Icons.Rounded.Search,null)},
            trailingIcon=if(state.query.isNotEmpty())({IconButton({state.query=""}){Icon(Icons.Rounded.Close,"Effacer la recherche")}}) else null,
            modifier=Modifier.fillMaxWidth().testTag("diagnostic-type-search"))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("${groups.size} types · exemples isolés",Modifier.weight(1f),fontSize=12.sp,color=Muted)
            DiagnosticIconAction("Tester tous les types",Icons.Rounded.PlaylistPlay,Modifier.testTag("diagnostic-types-test-all")){testAll(groups.values.map(QuestionUiVariants::primary))}
            DiagnosticIconAction("Tester tous les types et toutes les variantes",Icons.Rounded.PlaylistAddCheck,Modifier.testTag("diagnostic-types-test-variants-all")){testAll(groups.values.flatMap(QuestionUiVariants::allCompositions))}
        }
        LazyColumn(state=state.listState,contentPadding=PaddingValues(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.weight(1f).testTag("diagnostic-types")) {
            rows.forEach {(kind,questions,variants)->
                item(key="type-$kind") {
                    val primary=QuestionUiVariants.primary(questions)
                    DiagnosticPanel(Modifier.testTag("diagnostic-type-$kind")) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${diagnosticTypeName(kind)} · ${questions.size}",fontSize=14.sp,fontWeight=FontWeight.Bold)
                                Text("Présentation courante"+(if(QuestionUiVariants.ids(primary)==listOf("standard"))"" else " · "+QuestionUiVariants.ids(primary).joinToString {QuestionUiVariants.label(it)}),fontSize=11.sp,lineHeight=14.sp,color=Muted)
                            }
                            DiagnosticIconAction("Tester un exemple de ${diagnosticTypeName(kind)}",Icons.Rounded.PlayArrow,Modifier.testTag("diagnostic-type-test-$kind")){test(QuestionUiVariants.primary(questions))}
                            DiagnosticIconAction("Tester toutes les présentations de ${diagnosticTypeName(kind)}",Icons.Rounded.PlaylistPlay,Modifier.testTag("diagnostic-type-test-variants-$kind")){testAll(QuestionUiVariants.allCompositions(questions))}
                            DiagnosticIconAction("Voir les questions de ${diagnosticTypeName(kind)}",Icons.Rounded.Search,Modifier.testTag("diagnostic-type-questions-$kind")){showQuestions(kind,"")}
                        }
                        if(variants.isNotEmpty()) {
                            val expanded=kind in state.expanded||state.query.isNotBlank()
                            Surface(onClick={state.expanded=if(kind in state.expanded)state.expanded-kind else state.expanded+kind},color=Mist,shape=RoundedCornerShape(6.dp),
                                modifier=Modifier.fillMaxWidth().testTag("diagnostic-type-variants-$kind")) {
                                Row(Modifier.heightIn(min=40.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Text("Variantes · ${variants.size}",Modifier.weight(1f),fontSize=12.sp,color=Teal)
                                    Icon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,if(expanded)"Replier les variantes" else "Déplier les variantes",Modifier.size(20.dp),tint=Teal)
                                }
                            }
                        }
                    }
                }
                if(kind in state.expanded||state.query.isNotBlank())items(variants,key={"$kind:${it.first.id}"}) {(variant,q)->
                    DiagnosticPanel(Modifier.padding(start=10.dp).testTag("diagnostic-variant-$kind-${variant.id}"),color=Mist) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(variant.title,fontSize=13.sp,fontWeight=FontWeight.Bold)
                                Text(variant.description,fontSize=11.sp,lineHeight=15.sp,color=Muted)
                            }
                            DiagnosticIconAction("Tester ${variant.title}",Icons.Rounded.PlayArrow,Modifier.testTag("diagnostic-variant-test-$kind-${variant.id}")){test(q)}
                            DiagnosticIconAction("Voir les questions : ${variant.title}",Icons.Rounded.Search,Modifier.testTag("diagnostic-variant-questions-$kind-${variant.id}")){showQuestions(kind,variant.id)}
                        }
                    }
                }
            }
            if(rows.isEmpty())item {Text("Aucune interface trouvée.",color=Muted)}
        }
    }
}
