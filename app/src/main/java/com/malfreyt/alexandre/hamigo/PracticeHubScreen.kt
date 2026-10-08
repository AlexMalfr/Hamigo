package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var customOpen by remember {mutableStateOf(false)}
    var custom by remember {mutableStateOf("100")}
    var customSelected by remember {mutableStateOf(false)}
    val available=remember(index,selected) {index.available(selected)}
    val listState=rememberLazyListState()
    var reviewCount by remember {mutableIntStateOf(10)}
    val progress=model.displayedProgress ?: model.progress
    val reviews=progress.reviews
    val completed=progress.completed
    val reviewPool=remember(content,completed,reviews) {CourseRevisionBuilder.eligible(content.lessons,completed,reviews)}
    val dueCount=reviewPool.count {reviews[it.id]?.due?.let {date->date<=System.currentTimeMillis()}==true}
    LazyColumn(Modifier.fillMaxSize().testTag("practice-list"),state=listState,contentPadding=PaddingValues(start=16.dp,top=16.dp,end=16.dp,bottom=16.dp+LocalNavigationContentOverlap.current),verticalArrangement=Arrangement.spacedBy(12.dp)) {
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
            Text("Nombre de questions :",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Ink)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                listOf(10,20,40,80,150).forEach {n ->
                    Surface(onClick={count=n;customSelected=false},modifier=Modifier.weight(1f).height(40.dp),color=if(count==n&&!customSelected)Mist else Cream,shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),border=androidx.compose.foundation.BorderStroke(1.dp,if(count==n&&!customSelected)Teal else Color(0xFFD4DEDA))) {
                        Box(contentAlignment=Alignment.Center){Text("$n",fontSize=13.sp,fontWeight=if(count==n&&!customSelected)FontWeight.Bold else FontWeight.Normal,color=Ink)}
                    }
                }
                Surface(onClick={custom=count.toString();customOpen=true},modifier=Modifier.height(40.dp).widthIn(min=40.dp).testTag("custom-question-count").semantics {this.selected=customSelected},color=if(customSelected)Mist else Cream,shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),border=androidx.compose.foundation.BorderStroke(1.dp,if(customSelected)Teal else Color(0xFFD4DEDA))) {
                    Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterHorizontally)) {
                        Icon(Icons.Rounded.Edit,"Choisir un nombre personnalisé",modifier=Modifier.size(18.dp),tint=if(customSelected)Teal else Purple)
                        if(customSelected)Text("$count",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Teal)
                    }
                }
            }
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
            Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Rounded.History,null,tint=Color(0xFF94651A));Spacer(Modifier.width(8.dp));Eyebrow("RÉVISIONS ALÉATOIRES",Color(0xFF94651A))}
            Text(if(reviewPool.isEmpty())"Réponds aux questions du Parcours pour retrouver ici les notions étudiées." else "Retrouve les notions étudiées, avec priorité aux questions à revoir. Les rappels sont mélangés dans la séance.",fontSize=13.sp,lineHeight=19.sp,color=Muted)
            if(reviewPool.isNotEmpty()) {
                Text("$dueCount à revoir",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Ink)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Questions :",fontSize=12.sp,color=Muted)
                    listOf(10,20,40).forEach { n ->FilterChip(reviewCount==n,{reviewCount=n},label={Text("$n",fontSize=13.sp)})}
                }
            }
            Action(if(reviewPool.isEmpty())"Lancer les révisions" else "Réviser · ${minOf(reviewCount,reviewPool.size)} questions",enabled=reviewPool.isNotEmpty()) {
                model.startQuestions("Révisions du parcours",CourseRevisionBuilder.create(reviewPool,reviews,reviewCount))
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
    if(customOpen) AlertDialog(onDismissRequest={customOpen=false},title={Text("Combien de questions ?")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(custom,{custom=it.filter(Char::isDigit).take(4)},singleLine=true,label={Text("De 1 à 1 000")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
    }},confirmButton={TextButton({count=custom.toInt();customSelected=true;customOpen=false},enabled=custom.toIntOrNull() in 1..1000) {Text("Choisir")}},dismissButton={TextButton({customOpen=false}) {Text("Annuler")}})
}
