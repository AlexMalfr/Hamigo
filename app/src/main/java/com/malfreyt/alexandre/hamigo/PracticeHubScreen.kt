package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Distinct modes; subthemes belong to the mix they configure. */
@OptIn(ExperimentalLayoutApi::class,ExperimentalFoundationApi::class)
@Composable fun PracticeHubScreen(model:AppModel,content:Content) {
    val index=content.mixIndex
    val groups=index.groups
    val keys=index.keys
    var selected by remember {mutableStateOf(keys)}
    var count by remember {mutableIntStateOf(20)}
    var themesOpen by remember {mutableStateOf(false)}
    var customSelected by remember {mutableStateOf(false)}
    val available=remember(index,selected) {index.available(selected)}
    val listState=rememberLazyListState()
    var reviewCount by remember {mutableIntStateOf(10)}
    var reviewCustomSelected by remember {mutableStateOf(false)}
    val progress=model.displayedProgress ?: model.progress
    val reviews=progress.reviews
    val completed=progress.completed
    val due=progress.due(content)
    val reviewPool=remember(content,completed,reviews,due) {CourseRevisionBuilder.eligible(content.lessons,completed,reviews,due)}
    val dueCount=due.size
    val effectiveReviewCount=if(reviewPool.isEmpty())0 else reviewCount.coerceIn(1,reviewPool.size)
    val reviewOptions=questionCountOptions(reviewPool.size)
    val effectiveReviewCustom=effectiveReviewCount==reviewCount&&(reviewCustomSelected||effectiveReviewCount !in reviewOptions)
    LaunchedEffect(reviewPool.size) {
        if(reviewPool.isNotEmpty()) {
            if(reviewCount!=effectiveReviewCount) {reviewCount=effectiveReviewCount;reviewCustomSelected=false}
            else if(reviewCount !in reviewOptions)reviewCustomSelected=true
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("practice-list"),state=listState,contentPadding=PaddingValues(start=16.dp,top=16.dp,end=16.dp,bottom=56.dp+LocalNavigationContentOverlap.current),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        stickyHeader {Column(Modifier.fillMaxWidth().stickyHeaderShadow(listState).padding(vertical=8.dp)) {BigTitle("À toi de jouer","Entraînement, révisions et examen blanc.")}}
        item {Panel(color=Color(0xFFFFE8E0)) {
            Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Rounded.School,null,tint=Coral);Spacer(Modifier.width(8.dp));Eyebrow("EXAMEN BLANC",Color(0xFFAC493B))}
            Text("20 questions réglementation en 15 min, puis 20 technique en 30 min. Il faut 10/20 dans chaque partie.",fontSize=13.sp,lineHeight=19.sp)
            Action("Lancer un examen blanc") {
                val questions=content.activeExam.filter {it.section=="regulation"}.shuffled().take(20)+content.activeExam.filter {it.section=="technique"}.shuffled().take(20)
                model.startQuestions("Examen blanc",questions,exam=true)
            }
        }}
        item {Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Rounded.Shuffle,null,tint=Purple);Spacer(Modifier.width(8.dp));Eyebrow("MIX SUR MESURE",Purple)}
            QuestionCountSelector(count,customSelected,{n,isCustom ->count=n;customSelected=isCustom},"mix-question-count","custom-question-count")
            if(count>available)Text("Choisis plus de thèmes ou réduis la longueur du mix.",fontSize=12.sp,color=Coral)
            Action("Lancer mon mix",enabled=count in 1..available) {
                model.startQuestions("Mix radio · $count questions",index.questions(selected,count))
            }
            TextButton({themesOpen=!themesOpen},Modifier.fillMaxWidth()) {Icon(if(themesOpen)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null);Spacer(Modifier.width(8.dp));Text("Choisir les thèmes · ${selected.size}/${keys.size}")}
            if(themesOpen) {
                TextButton({selected=if(selected==keys)emptySet() else keys},contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)) {Text(if(selected==keys)"Tout désélectionner" else "Tout sélectionner",fontSize=12.sp)}
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                listOf("regulation" to "Réglementation","technique" to "Technique").forEach {(section,label) ->
                    val topics=groups[section].orEmpty()
                    val groupKeys=topics.keys.map {"$section|$it"}.toSet()
                    val checked=groupKeys.count {it in selected}
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        TriStateCheckbox(if(checked==0)ToggleableState.Off else if(checked==groupKeys.size)ToggleableState.On else ToggleableState.Indeterminate,{selected=if(checked==groupKeys.size)selected-groupKeys else selected+groupKeys})
                        Text(label,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Ink,fontSize=14.sp)
                        Text("$checked/${groupKeys.size}",fontSize=12.sp,color=Muted)
                    }
                    FlowRow(Modifier.padding(start=30.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        topics.keys.forEach {topic ->val key="$section|$topic"
                            FilterChip(key in selected,{selected=if(key in selected)selected-key else selected+key},label={Text(topic,fontSize=11.sp)})
                        }
                    }
                }
                }
            }
        }}
        item {Panel(color=Color(0xFFFFF0CF)) {
            Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Rounded.History,null,tint=Color(0xFF94651A));Spacer(Modifier.width(8.dp));Eyebrow("RÉVISIONS",Color(0xFF94651A))}
            val reviewSummary="($dueCount à revoir · ${reviewPool.size} question${if(reviewPool.size==1)"" else "s"} disponible${if(reviewPool.size==1)"" else "s"})."
            Text(if(reviewPool.isEmpty())"Réponds aux cours ou révise les fiches Mémo pour retrouver ici les notions étudiées $reviewSummary" else "Retrouve les notions étudiées, avec priorité aux questions à revoir. Les rappels sont mélangés dans la séance $reviewSummary",fontSize=13.sp,lineHeight=19.sp,color=Muted)
            if(reviewPool.isNotEmpty()) {
                QuestionCountSelector(effectiveReviewCount,effectiveReviewCustom,{n,isCustom ->reviewCount=n;reviewCustomSelected=isCustom},"review-question-count","custom-review-question-count",availableCount=reviewPool.size)
            }
            Action("Lancer les révisions",enabled=reviewPool.isNotEmpty()) {
                model.startQuestions("Révisions",CourseRevisionBuilder.create(reviewPool,reviews,effectiveReviewCount))
            }
        }}
        item {Panel(color=Mist) {
            Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Rounded.Science,null,tint=Teal);Spacer(Modifier.width(8.dp));Eyebrow("LABO DES CALCULS")}
            Text("Ohm, dB, réactances, circuits et ondes : des exercices générés à chaque séance.",fontSize=13.sp,color=Muted,lineHeight=19.sp)
            Action("Ouvrir le labo · 12 questions") {model.startQuestions("Labo des calculs",ExtendedPracticeGenerator.create(Random.nextInt(),12).filter {it.kind in setOf("number","estimate","resistor","binary","frequency")}.let {initial ->
                (initial+content.procedural.filter {it.kind in setOf("number","estimate","resistor","binary","frequency")}.shuffled()).distinctBy {it.id}.take(12)
            })}
        }}
        item {Text("L’examen blanc utilise Exam1 REF, avec ses sources et illustrations. Le mix ajoute les variantes Hamigo.",fontSize=11.sp,lineHeight=17.sp,color=Muted)}
    }
}
