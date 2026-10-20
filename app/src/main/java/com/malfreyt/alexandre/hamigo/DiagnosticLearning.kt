package com.malfreyt.alexandre.hamigo

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val LocalDiagnosticHudVisible=staticCompositionLocalOf {true}

@Composable internal fun DiagnosticQuestionJson(question:Question,onClose:()->Unit) {
    val context=LocalContext.current
    val json=remember(question){questionDebugJson(question)}
    var copied by remember(json){mutableStateOf(false)}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(12.dp).testTag("diagnostic-json-dialog"),
            color=Cream,shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Question · JSON",fontWeight=FontWeight.ExtraBold,fontSize=21.sp)
                Text("Définition d’origine et question de la séance, avec ses valeurs, réponses et graine d’affichage.",fontSize=12.sp,lineHeight=17.sp,color=Muted)
                SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(json,Modifier.testTag("diagnostic-question-json"),fontFamily=FontFamily.Monospace,fontSize=11.sp,lineHeight=16.sp)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Question Hamigo · JSON",json))
                        copied=true
                    },Modifier.weight(1f).testTag("diagnostic-json-copy")) {
                        Icon(Icons.Rounded.ContentCopy,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(copied)"Copié" else "Copier")
                    }
                    Button(onClose,Modifier.weight(1f).testTag("diagnostic-json-close")){Text("Fermer")}
                }
            }
        }
    }
}

internal class DiagnosticSkillsState {
    var query by mutableStateOf("")
    var expanded by mutableStateOf<Set<String>>(emptySet())
}

@Composable internal fun DiagnosticSkills(owner:AppModel,content:Content,state:DiagnosticSkillsState,listState:LazyListState,onQuestions:(String)->Unit) {
    // Deliberately read the owner, not the RAM-only inspector or displayed sandbox.
    val reviews=remember(owner,owner.revision){owner.progress.skillReviews}
    val titles=remember(content){content.skills.associate {it.id to it.title}}
    val skills=remember(reviews,titles,state.query){
        val words=NarrationAudit.fold(state.query).split(Regex("\\s+")).filter(String::isNotBlank)
        (titles.keys+reviews.keys).sorted().filter {id->
            val text=NarrationAudit.fold("$id ${titles[id].orEmpty()} ${SkillHierarchy.ancestry(id).joinToString {it.title}}")
            words.all {it in text}
        }
    }
    val now=System.currentTimeMillis()
    val rows=remember(skills,state.expanded,state.query){buildList<Pair<String,Int>> {
        fun append(family:SkillFamily,depth:Int) {
            val descendants=SkillHierarchy.descendants(family.id).filter {it in skills}
            if(descendants.isEmpty())return
            add(family.id to depth)
            if(family.id in state.expanded||state.query.isNotBlank()) {
                SkillHierarchy.families.filter {it.parentId==family.id}.forEach {append(it,depth+1)}
                family.skillIds.filter {it in skills}.forEach {add(it to depth+1)}
            }
        }
        SkillHierarchy.families.filter {it.parentId==null}.forEach {append(it,0)}
        skills.filter {SkillHierarchy.familyFor(it)==null}.forEach {add(it to 0)}
    }}
    val dateFormat=remember {DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())}
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=8.dp)) {
        OutlinedTextField(state.query,{state.query=it},label={Text("Famille, notion ou identifiant")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,
            trailingIcon=if(state.query.isNotEmpty())({IconButton({state.query=""}){Icon(Icons.Rounded.Close,"Effacer la recherche")}}) else null,
            modifier=Modifier.fillMaxWidth().testTag("diagnostic-skill-search"))
        Text("${reviews.size} évaluées · ${reviews.count {SkillLearning.isSrsSkill(it.key)&&it.value.mastered}} consolidées · profil réel, lecture seule",fontSize=11.sp,lineHeight=15.sp,color=Teal,modifier=Modifier.padding(vertical=4.dp))
        LazyColumn(Modifier.weight(1f).testTag("diagnostic-skills"),state=listState,contentPadding=PaddingValues(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            item {Text("Familles : synthèse des feuilles, sans maîtrise propre. Compteurs « ≥ » : minimum connu après fusion.",fontSize=10.sp,lineHeight=13.sp,color=Muted)}
            items(rows,key={it.first}) {(id,depth)->
                val family=SkillHierarchy.family(id)
                if(family!=null)DiagnosticPanel(modifier=Modifier.padding(start=(depth*8).dp).testTag("diagnostic-family-$id"),color=Mist) {
                    val summary=SkillHierarchy.summary(family,reviews,now)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Surface(onClick={state.expanded=if(id in state.expanded)state.expanded-id else state.expanded+id},color=Mist,modifier=Modifier.weight(1f).testTag("diagnostic-family-expand-$id")) {
                            Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(if(id in state.expanded||state.query.isNotBlank())Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,null,Modifier.size(20.dp),tint=Teal)
                                Column(Modifier.weight(1f)) {
                                    Text(family.title,fontSize=13.sp,lineHeight=16.sp,fontWeight=FontWeight.Bold)
                                    Text("${summary.encountered} évaluées · ${summary.fragile} fragiles · ${summary.due} dues",fontSize=10.sp,lineHeight=14.sp,color=Muted)
                                }
                            }
                        }
                        DiagnosticIconAction("Voir les questions de ${family.title}",Icons.Rounded.Search,Modifier.testTag("diagnostic-family-questions-$id")){onQuestions(id)}
                    }
                } else DiagnosticPanel(modifier=Modifier.padding(start=(depth*8).dp).testTag("diagnostic-skill-$id")) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(titles[id] ?: id,fontSize=13.sp,lineHeight=16.sp,fontWeight=FontWeight.Bold)
                        SelectionContainer {Text(id,fontSize=10.sp,lineHeight=13.sp,color=Teal,fontFamily=FontFamily.Monospace)}
                    }
                    DiagnosticIconAction("Voir les questions de ${titles[id] ?: id}",Icons.Rounded.Search,Modifier.testTag("diagnostic-skill-open-$id")){onQuestions(id)}
                }
                val review=reviews[id]
                if(review==null)Text("Pas encore évaluée",fontSize=11.sp,lineHeight=14.sp,color=Muted)
                else {
                    if(SkillLearning.isSrsSkill(id)) {
                        Text("Due ${if(review.due==0L)"maintenant" else dateFormat.format(Instant.ofEpochMilli(review.due))} · ${formatNumber(review.interval)} j · facilité ${formatNumber(review.ease)}",fontSize=11.sp,lineHeight=15.sp)
                        Text("Répétitions ${review.repetitions} · erreurs ≥ ${review.lapses} · ${if(review.mastered)"consolidée" else "à consolider"}${if(review.seeded)" · historique repris" else ""}",fontSize=11.sp,lineHeight=15.sp,color=if(review.mastered)Teal else Muted)
                    } else Text("Hors SRS · utilisation de Hamigo",fontSize=11.sp,lineHeight=15.sp,color=Muted)
                    Text("Tentatives ≥ ${review.attempts} · justes ≥ ${review.successes}",fontSize=11.sp,lineHeight=15.sp)
                    if(review.lastQuestionId.isNotBlank())SelectionContainer {Text("Dernière : ${review.lastQuestionId}",fontSize=10.sp,lineHeight=14.sp,fontFamily=FontFamily.Monospace,color=Muted)}
                }
            }}
            if(skills.isEmpty())item {Text("Aucune notion trouvée.",Modifier.padding(vertical=16.dp),color=Muted)}
        }
    }
}

/** The native guide runs against RAM preferences and can complete without any real award. */
@Composable internal fun DiagnosticHamigoGuide(owner:AppModel,content:Content,onClose:()->Unit) {
    val context=LocalContext.current
    val uiState=owner.diagnosticUiState
    val model=remember(owner,content){uiState.preview("Guide Hamigo"){AppModel().apply {
        initializeDiagnostics(context,owner,content)
        DiagnosticData.seed(this,kind="empty",completed=0)
        showWelcome=false
        beginTour()
    }}}
    DisposableEffect(model){onDispose {if(!owner.debugToolsOpen||uiState.mock!="Guide Hamigo")uiState.disposePreview()}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize().testTag("diagnostic-hamigo-guide"),color=Cream) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Guide Hamigo · simulation",Modifier.weight(1f),fontSize=13.sp,color=Teal,fontWeight=FontWeight.Bold)
                    IconButton({model.beginTour()},Modifier.testTag("diagnostic-guide-restart")){Icon(Icons.Rounded.Refresh,"Recommencer le guide")}
                    IconButton(onClose,Modifier.testTag("diagnostic-guide-close")){Icon(Icons.Rounded.Close,"Fermer la simulation du guide")}
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    FeedbackPreferenceScope(owner.interactionFeedback,model.progress.prefs) {
                        CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false,LocalDiagnosticHudVisible provides false) {
                            HamigoAppContent(model,model,diagnosticPreview=false)
                        }
                    }
                }
            }
        }
    }
}
