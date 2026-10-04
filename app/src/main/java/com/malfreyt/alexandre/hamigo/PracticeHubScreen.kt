package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import kotlin.random.Random

/** Distinct modes; subthemes belong to the mix they configure. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PracticeHubScreen(model:AppModel,content:Content) {
    val groups=remember(content) {content.activeExam.groupBy {it.section}.mapValues {(_,questions)->questions.groupBy {it.topic}.toSortedMap()}}
    val keys=remember(groups) {groups.flatMap {(section,topics)->topics.keys.map {"$section|$it"}}.toSet()}
    var selected by remember {mutableStateOf(keys)}
    var count by remember {mutableIntStateOf(20)}
    var themesOpen by remember {mutableStateOf(false)}
    var customOpen by remember {mutableStateOf(false)}
    var custom by remember {mutableStateOf("100")}
    val pool=content.activeExam.filter {"${it.section}|${it.topic}" in selected}
    val generated=content.procedural.filter {q ->
        selected==keys || selected.any {key ->key.startsWith("technique|") && generatedMatchesTopic(q.topic,key.substringAfter('|'))}
    }
    val available=pool.size+generated.size
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {BigTitle("À toi de jouer","Trois façons de renforcer ton signal.")}
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
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                listOf(10,20,40,80,150).forEach {n ->
                    Surface(onClick={count=n},modifier=Modifier.weight(1f).height(40.dp),color=if(count==n)Mist else Cream,shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),border=androidx.compose.foundation.BorderStroke(1.dp,if(count==n)Teal else Color(0xFFD4DEDA))) {
                        Box(contentAlignment=Alignment.Center){Text("$n",fontSize=13.sp,fontWeight=if(count==n)FontWeight.Bold else FontWeight.Normal,color=Ink)}
                    }
                }
                IconButton({custom=count.toString();customOpen=true},Modifier.size(40.dp)) {Icon(Icons.Rounded.Edit,"Choisir un nombre personnalisé",modifier=Modifier.size(20.dp),tint=Purple)}
            }
            Text("$count questions · ${selected.size}/${keys.size} thèmes",fontSize=12.sp,color=Muted)
            if(count>available)Text("Choisis plus de thèmes ou réduis la longueur du mix.",fontSize=12.sp,color=Coral)
            Action("Lancer mon mix",enabled=count in 1..available) {
                val generatedCount=if(generated.isEmpty())0 else minOf((count*.3).toInt().coerceAtLeast(1),generated.size)
                val fixedCount=minOf(count-generatedCount,pool.size)
                val finalGenerated=minOf(count-fixedCount,generated.size)
                model.startQuestions("Mix radio · $count questions",(pool.shuffled().take(fixedCount)+generated.shuffled().take(finalGenerated)).shuffled())
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
    }},confirmButton={TextButton({count=custom.toInt();customOpen=false},enabled=custom.toIntOrNull() in 1..1000) {Text("Choisir")}},dismissButton={TextButton({customOpen=false}) {Text("Annuler")}})
}

private fun generatedMatchesTopic(generated:String,topic:String):Boolean {
    fun normalized(value:String)=Normalizer.normalize(value.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    val target=normalized(topic)
    val terms=when(generated) {
        "Loi d'Ohm","Grandeurs électriques","Unités et préfixes" ->listOf("electricite","continu","ohm")
        "Associations de résistances" ->listOf("resistance")
        "Associations de condensateurs","Réactances" ->listOf("condensateur","bobine","rlc","alternatif")
        "Décibels" ->listOf("decibel","db","puissance")
        "Longueur d'onde" ->listOf("antenne","propagation","frequence")
        "Numérique" ->listOf("numerique","code","modulation")
        "Morse" ->listOf("morse","telegraphie")
        "Modulation" ->listOf("modulation","signal")
        else ->listOf(normalized(generated))
    }
    return terms.any {it in target}
}
