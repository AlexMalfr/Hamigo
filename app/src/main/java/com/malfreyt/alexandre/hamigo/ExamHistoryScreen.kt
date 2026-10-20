package com.malfreyt.alexandre.hamigo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ExamHistoryScreen(model:AppModel,content:Content) {
    val progress=model.displayedProgress ?: model.progress
    val attempts=remember(progress,model.revision){progress.examHistory}
    var transcriptId by rememberSaveable {mutableStateOf<String?>(null)}
    val listState=rememberLazyListState()
    var allTopics by rememberSaveable {mutableStateOf(false)}
    val transcriptStates=rememberSaveableStateHolder()
    val selected=attempts.firstOrNull {it.id==transcriptId}
    BackHandler(selected!=null){transcriptId=null}
    if(selected!=null) {
        transcriptStates.SaveableStateProvider(selected.id) {ExamTranscriptScreen(selected,content){transcriptId=null}}
        return
    }
    val mistakes=remember(attempts){ExamHistory.topicMistakes(attempts)}
    val unlocked=attempts.size>=3
    val canCustomize=unlocked&&content.examSnapshot!=null&&listOf("regulation","technique").all {part->content.activeExam.count {it.section==part}>=20}
    LazyColumn(Modifier.fillMaxSize().testTag("exam-history-list"),state=listState,
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=56.dp+LocalNavigationContentOverlap.current),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        stickyHeader(key="exam-history-header") {
            Column(Modifier.fillMaxWidth().stickyHeaderShadow(listState).background(Cream).padding(vertical=8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(feedbackClick {model.route="practice"},Modifier.offset(x=(-12).dp)){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Revenir aux défis")}
                    Text("Tes examens blancs",fontSize=25.sp,fontWeight=FontWeight.ExtraBold)
                }
                Text("Résultats, points à revoir et corrections.",fontSize=13.sp,color=Muted)
            }
        }
        if(attempts.isEmpty())item(key="exam-history-empty") {
            Panel(color=Mist) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Pico(Modifier.size(90.dp),mood=MascotMood.DETERMINED)
                    Text("Ton premier examen ouvre l’historique.",Modifier.weight(1f),fontSize=19.sp,lineHeight=25.sp,fontWeight=FontWeight.Bold)
                }
                Text("Termine les deux parties pour conserver tes résultats et retrouver chaque question avec sa correction. Les anciens examens ne sont pas reconstitués.",fontSize=14.sp,lineHeight=21.sp,color=Muted)
                Action("Retrouver l’examen blanc"){model.route="practice"}
            }
        }
        if(attempts.isNotEmpty())item(key="exam-score-evolution") {
            Panel {
                Text("L’évolution de tes résultats",fontSize=20.sp,fontWeight=FontWeight.Bold)
                ExamScoreChart(attempts)
                val latest=attempts.first()
                Text("Dernier examen · ${examAttemptDate(latest.at)}",fontSize=12.sp,color=Muted)
                ExamScoreRow(latest)
                Text("Le repère reste 10/20 dans chaque partie. Le total seul ne suffit pas.",fontSize=12.sp,lineHeight=18.sp,color=Muted)
            }
        }
        item(key="customized-exam") {
            Panel(color=Color(0xFFFFF0CF)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(if(unlocked)Icons.Rounded.Tune else Icons.Rounded.Lock,null,tint=Color(0xFF94651A))
                    Text("Un examen pour tes points faibles",Modifier.weight(1f),fontSize=19.sp,fontWeight=FontWeight.Bold)
                }
                Text(if(unlocked)"Les sujets sur lesquels tu fais des erreurs reviennent davantage, avec une sélection variée. Même format : 20 questions de réglementation, puis 20 de technique."
                    else "Disponible après trois examens complets : ${attempts.size.coerceAtMost(3)} / 3. Tes erreurs guideront la sélection, tout en gardant plusieurs sujets.",fontSize=13.sp,lineHeight=20.sp,color=Muted)
                Action("Lancer un examen personnalisé",modifier=Modifier.testTag("start-customized-exam"),enabled=canCustomize) {
                    model.startQuestions("Examen personnalisé",ExamHistory.customizedQuestions(content.activeExam,attempts),exam=true)
                }
                if(unlocked&&content.examSnapshot==null)Text("Télécharge la banque Exam’1 depuis Défis pour le lancer.",fontSize=12.sp,color=Muted)
            }
        }
        if(mistakes.isNotEmpty())item(key="exam-error-topics") {
            Panel {
                Text("Les sujets à retravailler",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text("Erreurs et questions laissées sans réponse, sur les examens conservés.",fontSize=12.sp,lineHeight=18.sp,color=Muted)
                (if(allTopics)mistakes else mistakes.take(6)).forEach {topic->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(topic.topic,fontSize=14.sp,fontWeight=FontWeight.Bold)
                        Text("${if(topic.section=="regulation")"Réglementation" else "Technique"} · ${topic.errors} / ${topic.attempts} à revoir",fontSize=12.sp,color=Muted)
                        LinearProgressIndicator(progress={topic.rate},modifier=Modifier.fillMaxWidth().height(5.dp),color=Coral,trackColor=Coral.copy(alpha=.12f))
                    }
                }
                if(mistakes.size>6)TextButton(feedbackClick {allTopics=!allTopics},contentPadding=PaddingValues(horizontal=0.dp)) {Text(if(allTopics)"Réduire la liste" else "Voir les ${mistakes.size} sujets")}
            }
        }
        if(attempts.isNotEmpty())item {BigTitle("Tes comptes rendus","${attempts.size} examen${if(attempts.size==1)"" else "s"} conservé${if(attempts.size==1)"" else "s"} · les 100 derniers au maximum")}
        items(attempts,key={it.id}) {attempt->
            Surface(onClick=feedbackClick {transcriptId=attempt.id},shape=RoundedCornerShape(20.dp),color=Color.White,modifier=Modifier.testTag("exam-attempt-${attempt.id}")) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(if(attempt.passed)Icons.Rounded.CheckCircle else Icons.Rounded.History,null,tint=if(attempt.passed)Teal else Purple)
                        Column(Modifier.weight(1f)) {
                            Text(examAttemptDate(attempt.at),fontSize=15.sp,fontWeight=FontWeight.Bold)
                            Text("${if(attempt.customized)"Examen personnalisé" else "Examen blanc"} · ${examDuration(attempt.elapsedMillis)}",fontSize=12.sp,color=Muted)
                        }
                        Icon(Icons.Rounded.ChevronRight,"Voir les 40 questions et leurs corrections",tint=Muted)
                    }
                    ExamScoreRow(attempt)
                }
            }
        }
        if(attempts.isNotEmpty())item {Text("Les textes et les réponses sont conservés dans ta sauvegarde complète. Les illustrations restent liées à la banque téléchargée ; aucune image n’est ajoutée à la sauvegarde ni au résumé social.",fontSize=11.sp,lineHeight=17.sp,color=Muted)}
    }
}

@Composable private fun ExamScoreRow(attempt:ExamAttempt) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Réglementation","${attempt.regulationScore}/20",Purple),Triple("Technique","${attempt.techniqueScore}/20",Teal),Triple("Total","${attempt.totalScore}/40",Color(0xFFBE6E2D))).forEach {(label,value,color)->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(value,fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=color)
                Text(label,fontSize=11.sp,color=Muted)
            }
        }
    }
}

@Composable private fun ExamScoreChart(attempts:List<ExamAttempt>) {
    val chronological=remember(attempts){attempts.sortedWith(compareBy<ExamAttempt> {it.at}.thenBy {it.id}).takeLast(20)}
    val series=listOf(Triple("Réglementation /20",Purple,{a:ExamAttempt->a.regulationScore/20f}),Triple("Technique /20",Teal,{a:ExamAttempt->a.techniqueScore/20f}),Triple("Total /40",Color(0xFFBE6E2D),{a:ExamAttempt->a.totalScore/40f}))
    val last=chronological.last()
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {series.forEach {(label,color,_)->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Box(Modifier.width(18.dp).height(3.dp).background(color,RoundedCornerShape(2.dp)));Text(label,fontSize=11.sp,color=Muted)}
        }}
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(174.dp).padding(end=7.dp),verticalArrangement=Arrangement.SpaceBetween) {Text("100 %",fontSize=10.sp,color=Muted);Text("50 %",fontSize=10.sp,color=Muted);Text("0 %",fontSize=10.sp,color=Muted)}
            Canvas(Modifier.weight(1f).height(174.dp).testTag("exam-score-chart").clearAndSetSemantics {
                contentDescription="Évolution sur ${chronological.size} examens. Dernier résultat : réglementation ${last.regulationScore} sur 20, technique ${last.techniqueScore} sur 20, total ${last.totalScore} sur 40."
            }) {
                val pad=6.dp.toPx();val w=(size.width-2*pad).coerceAtLeast(1f);val h=(size.height-2*pad).coerceAtLeast(1f)
                val withTrend=chronological.size>=3;val divisor=(chronological.lastIndex+if(withTrend)1 else 0).coerceAtLeast(1)
                fun point(index:Int,value:Float)=Offset(pad+w*index/divisor,pad+h*(1-value.coerceIn(0f,1f)))
                listOf(0f,.5f,1f).forEach {fraction->drawLine(Mist,point(0,fraction),Offset(size.width-pad,point(0,fraction).y),1.dp.toPx())}
                series.forEach {(label,color,score)->
                    val path=Path();chronological.forEachIndexed {i,a->val p=point(i,score(a));if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)}
                    drawPath(path,color,style=Stroke(2.2.dp.toPx(),cap=StrokeCap.Round))
                    chronological.forEachIndexed {i,a->drawCircle(color,2.8.dp.toPx(),point(i,score(a)))}
                    ExamHistory.trend(chronological,score)?.let {projected->drawLine(color.copy(alpha=.65f),point(chronological.lastIndex,score(last)),point(chronological.size,projected),2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))}
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(examAttemptDate(chronological.first().at,short=true),fontSize=10.sp,color=Muted);Text(if(chronological.size>=3)"Tendance →" else examAttemptDate(last.at,short=true),fontSize=10.sp,color=Muted)}
        Text(if(chronological.size>=3)"Pointillés : prolongement de la tendance des derniers résultats, sans prédiction de réussite. Les examens personnalisés ciblent tes difficultés et ne sont pas directement comparables aux tirages ordinaires."
            else "Une tendance descriptive apparaîtra après trois examens complets.",fontSize=11.sp,lineHeight=17.sp,color=Muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ExamTranscriptScreen(attempt:ExamAttempt,content:Content,onBack:()->Unit) {
    val state=rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().testTag("exam-transcript-list"),state=state,
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=56.dp+LocalNavigationContentOverlap.current),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().stickyHeaderShadow(state).background(Cream).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(feedbackClick(onBack)){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Revenir à l’historique")}
                Column(Modifier.weight(1f)) {Text("Les 40 questions",fontSize=22.sp,fontWeight=FontWeight.ExtraBold);Text(examAttemptDate(attempt.at),fontSize=12.sp,color=Muted)}
            }
        }
        item {Panel(color=Mist) {ExamScoreRow(attempt);Text("${if(attempt.customized)"Examen personnalisé" else "Examen blanc"} · durée ${examDuration(attempt.elapsedMillis)}",fontSize=12.sp,color=Muted)}}
        items(attempt.responses,key={it.index}) {response->
            Panel(modifier=Modifier.testTag("exam-response-${response.index}")) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    Icon(if(response.correct)Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,null,tint=if(response.correct)Teal else Coral,modifier=Modifier.size(19.dp))
                    Text("${response.index+1} · ${if(response.index<20)"Réglementation" else "Technique"}",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Muted)
                }
                Text(response.prompt,fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.Bold)
                if(response.imageReference!=null) {
                    val image=rememberExamArtwork(response.imageReference)
                    var enlarge by rememberSaveable {mutableStateOf(false)}
                    if(image!=null) {
                        ExamIllustration(image,{enlarge=true})
                        if(enlarge)FullscreenExamIllustration(image.original){enlarge=false}
                    } else Text("Illustration indisponible : la génération de la banque n’est plus téléchargée. La référence source est conservée ci-dessous.",fontSize=12.sp,lineHeight=18.sp,color=Muted)
                }
                if(response.omitted)Text("Ta réponse : sans réponse",fontSize=14.sp,color=Muted)
                response.choices.forEachIndexed {index,choice->
                    val expected=index==response.correctAnswer
                    val chosen=!response.omitted&&index==response.selectedAnswer
                    val color=if(expected)Teal else if(chosen)Color(0xFFB54B3D) else Muted
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
                        Text("${('A'.code+index).toChar()}.",fontSize=14.sp,color=color,fontWeight=FontWeight.Bold)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(choice,fontSize=14.sp,lineHeight=20.sp,color=color,fontWeight=if(expected||chosen)FontWeight.Bold else FontWeight.Normal)
                            if(expected||chosen)Text(when {expected&&chosen->"Ta réponse · réponse attendue";expected->"Réponse attendue";else->"Ta réponse"},fontSize=11.sp,color=color)
                        }
                    }
                }
                if(response.explanation.isNotBlank())Text(response.explanation,fontSize=14.sp,lineHeight=21.sp,color=Ink)
                Text("Sujet : ${response.topic.ifBlank {"Non précisé"}}",fontSize=11.sp,color=Muted)
                Text("Question ${response.questionId} · ${response.source}",fontSize=10.sp,lineHeight=15.sp,color=Muted)
                if(response.imageReference!=null)Text("Illustration : ${response.imageReference}",fontSize=10.sp,lineHeight=15.sp,color=Muted)
            }
        }
    }
}

internal fun examAttemptDate(at:Long,short:Boolean=false):String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(if(short)"d MMM" else "d MMM yyyy à HH:mm",Locale.FRENCH))
