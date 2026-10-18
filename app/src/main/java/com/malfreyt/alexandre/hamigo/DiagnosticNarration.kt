package com.malfreyt.alexandre.hamigo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.Normalizer
import java.util.Locale

/** Search the displayed AND spoken content without changing any lesson identity or progress. */
internal data class DiagnosticCourseEntry(val lesson:Lesson,val location:String) {
    val source=(listOf(lesson.title,lesson.summary)+lesson.body+lesson.formula).joinToString("\n\n")
    val spoken=LessonNarration.segments(lesson,4000)
    val warnings=NarrationAudit.warnings(source,spoken.joinToString(" "))
    val searchText=NarrationAudit.fold(listOf(lesson.id,location,lesson.topic,source,spoken.joinToString(" ")).joinToString(" "))
    val hasFormula=lesson.formula.isNotBlank()||lesson.body.any {it.contains('=')}
}

internal object NarrationAudit {
    fun fold(text:String)=Normalizer.normalize(text,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
    fun entries(chapters:List<Chapter>)=chapters.flatMapIndexed {ci,ch->ch.lessons.mapIndexed {li,lesson->
        DiagnosticCourseEntry(lesson,"${ci+1}. ${ch.title} → ${li+1}. ${lesson.title}")
    }}
    fun search(entries:List<DiagnosticCourseEntry>,query:String,filter:String=""):List<DiagnosticCourseEntry> {
        val words=fold(query).split(Regex("\\s+")).filter(String::isNotBlank)
        return entries.filter {entry->(filter!="formula"||entry.hasFormula)&&(filter!="warning"||entry.warnings.isNotEmpty())&&words.all(entry.searchText::contains)}
    }
    fun warnings(source:String,spoken:String):List<String> = buildList {
        val groups=Regex("\\b[A-Z]{2,}\\b").findAll(spoken).map {it.value}.distinct().toList()
        if(groups.isNotEmpty())add("Groupes en majuscules laissés au moteur : ${groups.joinToString(", ")}")
        if(Regex("[=²³½¼¾^√]|[A-Za-z][₀-₉]|\\b[A-Za-z]\\d+\\b").containsMatchIn(spoken))add("Notation mathématique encore présente dans le texte oral")
        if(source.contains('=')&&spoken.contains("pause entre mots"))add("Possible confusion entre une fraction et une séparation Morse")
        if(Regex("\\b(?:di|dI|du|dU)\\s+divisé par\\s+dt\\b").containsMatchIn(spoken))add("Différentielle encore écrite en notation technique")
        if(spoken.any {it in '\uE000'..'\uF8FF'})add("Marqueur interne non résolu")
    }
}

@Composable internal fun DiagnosticCourseCatalog(entries:List<DiagnosticCourseEntry>,loading:Boolean,query:String,changeQuery:(String)->Unit,
    filter:String,changeFilter:(String)->Unit,listState:LazyListState,choose:(DiagnosticCourseEntry)->Unit) {
    val matches=remember(entries,query,filter){NarrationAudit.search(entries,query,filter)}
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=14.dp)) {
        OutlinedTextField(query,changeQuery,label={Text("ID, titre, texte, formule ou TTS")},leadingIcon={Icon(Icons.Rounded.Search,null)},
            trailingIcon=if(query.isNotEmpty())({IconButton({changeQuery("")}){Icon(Icons.Rounded.Close,"Effacer la recherche")}}) else null,
            singleLine=true,modifier=Modifier.fillMaxWidth().testTag("diagnostic-course-search"))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("" to "Toutes","formula" to "Formules","warning" to "À contrôler").forEach {(value,label)->
                FilterChip(filter==value,{changeFilter(value)},label={Text(label,fontSize=11.sp)},modifier=Modifier.testTag("diagnostic-course-filter-${value.ifBlank {"all"}}"))
            }
        }
        Text("${matches.size} / ${entries.size} introductions",fontSize=12.sp,color=Muted)
        if(filter=="warning")Text("Repérage automatique des notations, pas une liste d’erreurs confirmées.",fontSize=11.sp,lineHeight=15.sp,color=Muted)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=8.dp))
        LazyColumn(Modifier.weight(1f).testTag("diagnostic-course-catalog"),state=listState,contentPadding=PaddingValues(top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(matches,key={it.lesson.id}) {entry->Surface(onClick={choose(entry)},shape=RoundedCornerShape(15.dp),color=Color.White,modifier=Modifier.testTag("diagnostic-course-${entry.lesson.id}")) {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(entry.lesson.title,fontWeight=FontWeight.Bold)
                    Text(entry.location,fontSize=11.sp,lineHeight=15.sp,color=Teal)
                    Text("${entry.lesson.id} · ${entry.lesson.questions.size} questions · ${entry.spoken.size} morceaux audio",fontSize=11.sp,color=Muted)
                    val terms=NarrationAudit.fold(query).split(Regex("\\s+")).filter(String::isNotBlank)
                    val excerpt=(entry.lesson.body+entry.lesson.formula+entry.spoken).firstOrNull {line->terms.isNotEmpty()&&terms.any {it in NarrationAudit.fold(line)}} ?: entry.lesson.summary
                    MorseAwareText(excerpt.take(220)+(if(excerpt.length>220)"…" else ""),fontSize=13.sp,lineHeight=18.sp)
                    if(entry.warnings.isNotEmpty())Text("${entry.warnings.size} notation${if(entry.warnings.size>1)"s" else ""} à contrôler",fontSize=11.sp,color=Coral)
                }
            }}
            if(!loading&&matches.isEmpty())item {Text("Aucune introduction trouvée.",Modifier.padding(vertical=16.dp),color=Muted)}
        }
    }
}

@Composable internal fun DiagnosticCourseDetail(entry:DiagnosticCourseEntry,open:()->Unit,test:()->Unit) {
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize().testTag("diagnostic-course-detail"),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Panel {
            Text(entry.lesson.title,fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text(entry.location,fontSize=12.sp,lineHeight=17.sp,color=Teal)
            SelectionContainer {Text(entry.lesson.id,fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=16.sp)}
            Button(open,Modifier.fillMaxWidth().testTag("diagnostic-course-open")){Icon(Icons.Rounded.MenuBook,null);Spacer(Modifier.width(8.dp));Text("Ouvrir l’introduction")}
            ActionLinks {
                ActionLink("Tester les questions · sans progression",Modifier.testTag("diagnostic-course-test"),icon=Icons.Rounded.Quiz,enabled=entry.lesson.questions.isNotEmpty(),onClick=test)
                ActionLink("Copier le contexte",icon=Icons.Rounded.ContentCopy) {copyDiagnosticText(context,"Contexte du cours","Hamigo ${BuildConfig.VERSION_NAME}\n${entry.lesson.id}\n${entry.location}")}
            }
        }}
        item {DiagnosticNarrationPanel(entry.spoken)}
        if(entry.warnings.isNotEmpty())item {Panel {Text("Notations à contrôler",fontWeight=FontWeight.Bold);entry.warnings.forEach {Text(it,fontSize=12.sp,color=Coral)}}}
        item {Panel {Text("Texte affiché dans l’app",fontWeight=FontWeight.Bold);SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            (entry.lesson.body+entry.lesson.formula).filter(String::isNotBlank).forEach {MorseAwareText(it,fontSize=14.sp,lineHeight=20.sp)}
        }}}}
    }
}

@Composable internal fun DiagnosticNarrationPanel(segments:List<String>) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val speech=remember(context){PreparedSpeech(context,Locale.FRANCE,"La voix française n’est pas disponible.",eager=false)}
    LaunchedEffect(segments){speech.stop()}
    DisposableEffect(speech,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)speech.stop()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);speech.close()}
    }
    Panel {
        Text("Texte exact envoyé au TTS",fontWeight=FontWeight.Bold)
        Row(verticalAlignment=Alignment.CenterVertically) {
            TalkingPico(Modifier.size(72.dp),talking=speech.speaking,speechActive=speech.active,mouthActivity=speech.mouthActivity,
                mood=if(speech.preparing)MascotMood.THINKING else MascotMood.HAPPY)
            Column(Modifier.weight(1f)) {
                Text("${segments.size} morceau${if(segments.size>1)"x" else ""} · ${segments.sumOf(String::length)} caractère${if(segments.sumOf(String::length)>1)"s" else ""}",fontSize=11.sp,lineHeight=15.sp,color=Muted)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton({if(speech.active)speech.stop() else speech.start(segments)},Modifier.testTag("diagnostic-narration-toggle"),enabled=segments.isNotEmpty()) {
                        if(speech.preparing)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                        else Icon(if(speech.active)Icons.Rounded.Stop else Icons.Rounded.VolumeUp,null,Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp));Text(if(speech.active)"Arrêter" else "Écouter")
                    }
                    IconButton({copyDiagnosticText(context,"Texte TTS",segments.joinToString("\n\n"))},Modifier.testTag("diagnostic-narration-copy")){Icon(Icons.Rounded.ContentCopy,"Copier le texte TTS",Modifier.size(20.dp))}
                }
            }
        }
        SelectionContainer {Text(segments.joinToString("\n\n"),Modifier.testTag("diagnostic-narration-text"),fontSize=14.sp,lineHeight=21.sp)}
    }
}

@Composable internal fun DiagnosticTtsLab() {
    var text by remember {mutableStateOf("E = ½LI²")}
    val segments=remember(text){LessonNarration.segments(listOf(text),4000)}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("diagnostic-tts-lab"),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Text("Essai de prononciation",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Même préparation et même moteur que dans les cours. Aucun effet sur les données.",fontSize=12.sp,lineHeight=17.sp,color=Muted)}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("LI" to "E = ½LI²","Morse" to "@ : .--.-.","Unités" to "2,2 µF et 145,05 MHz").forEach {(label,value)->AssistChip({text=value},label={Text(label)})}
        }}
        item {OutlinedTextField(text,{text=it.take(16000)},label={Text("Texte à lire")},minLines=3,maxLines=7,modifier=Modifier.fillMaxWidth().testTag("diagnostic-tts-input"))}
        item {DiagnosticNarrationPanel(segments)}
        val warnings=NarrationAudit.warnings(text,segments.joinToString(" "))
        if(warnings.isNotEmpty())item {Panel {Text("À contrôler",fontWeight=FontWeight.Bold);warnings.forEach {Text(it,fontSize=12.sp,color=Coral)}}}
    }
}

private fun copyDiagnosticText(context:Context,label:String,text:String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label,text))
}
