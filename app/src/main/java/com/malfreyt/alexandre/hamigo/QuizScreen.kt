package com.malfreyt.alexandre.hamigo

import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import com.malfreyt.alexandre.hamigo.platform.NativeShare
import androidx.compose.ui.platform.LocalFocusManager
import kotlin.math.sin
import kotlin.random.Random

@Composable fun QuizScreen(model:AppModel) {
    val s=model.session ?: return
    val tick=model.revision
    if(s.done) {ResultsScreen(model,s);return}
    val q=s.current ?: return
    val key="${q.id}-${s.index}"
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val scroll=rememberScrollState()
    var choice by rememberSaveable(key){mutableIntStateOf(-1)}
    var numeric by rememberSaveable(key){mutableStateOf("")}
    var ordered by remember(key){mutableStateOf(emptyList<Int>())}
    var matches by remember(key){mutableStateOf(emptyMap<Int,Int>())}
    var left by remember(key){mutableStateOf<Int?>(null)}
    var flipped by rememberSaveable(key){mutableStateOf(false)}
    var frequency by rememberSaveable(key){mutableFloatStateOf(144f)}
    var selectedMany by remember(key){mutableStateOf(emptySet<Int>())}
    var morse by rememberSaveable(key){mutableStateOf("")}
    var binary by rememberSaveable(key){mutableIntStateOf(0)}
    val estimateMin=q.bands.getOrNull(0)?.toFloatOrNull() ?: 0f
    val estimateMax=(q.bands.getOrNull(1)?.toFloatOrNull() ?: 100f).coerceAtLeast(estimateMin+1f)
    var estimate by rememberSaveable(key){mutableFloatStateOf(estimateMin)}
    var quit by remember{mutableStateOf(false)}
    var enlarged by remember{mutableStateOf(false)}
    var timer by remember{mutableLongStateOf(s.examTimeRemaining)}
    val feedback=s.feedback
    LaunchedEffect(key){scroll.scrollTo(0);focus.clearFocus()}
    LaunchedEffect(key,feedback) {
        if(feedback!=null) {
            // Once answer widgets have laid out their feedback, make the explanation visible.
            delay(120)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }
    DisposableEffect(key){onDispose{stopMorse()}}
    val bitmap=remember(q.image){q.image?.let{path->runCatching{context.assets.open(path).use{BitmapFactory.decodeStream(it)?.asImageBitmap()}}.getOrNull()}}
    LaunchedEffect(s,s.index/20) {
        if(s.exam) while(!s.done) {timer=s.examTimeRemaining;if(timer<=0){model.timeoutExamPart();break};delay(1000)}
    }
    val canAnswer=when(q.kind) {
        "number"->numeric.isNotBlank();"order","sort"->ordered.size==q.choices.size
        "match"->matches.size==q.pairs.size;"frequency","binary","estimate"->true
        "multiselect"->selectedMany.isNotEmpty();"morseEncode"->morse.isNotBlank();else->choice>=0
    }
    fun check()=when(q.kind) {
        "number"->LearningRules.numericCorrect(numeric,q.value ?: 0.0,q.tolerance)
        "frequency"->kotlin.math.abs(frequency-(q.value ?: 0.0)) <= q.tolerance
        "estimate"->kotlin.math.abs(estimate-(q.value ?: 0.0)) <= q.tolerance
        "binary"->binary==(q.value ?: 0.0).toInt()
        "multiselect"->selectedMany==q.bands.mapNotNull{it.toIntOrNull()}.toSet()
        "morseEncode"->normalizeMorse(morse)==normalizeMorse(q.bands.firstOrNull().orEmpty())
        "order","sort"->ordered==q.choices.indices.toList()
        "match"->matches.all{it.key==it.value}
        else->choice==q.answer
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton({quit=true}){Icon(Icons.Rounded.Close,"Quitter la séance")}
            Column(Modifier.weight(1f)) {Text(s.title,maxLines=1,fontSize=13.sp,fontWeight=FontWeight.Bold,color=Muted);Spacer(Modifier.height(7.dp));LinearProgressIndicator(progress={(s.index.toFloat()/s.questions.size).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(8.dp),color=Teal,trackColor=Mist)}
            Spacer(Modifier.width(12.dp));Text(if(s.exam)"%02d:%02d".format(timer/60000,(timer/1000)%60) else "${s.index+1}/${s.questions.size}",fontSize=13.sp,fontWeight=FontWeight.Bold,color=if(s.exam&&timer<60000)Coral else Teal)
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(s.exam)Eyebrow(if(s.index<20)"RÉGLEMENTATION · 15 MIN" else "TECHNIQUE · 30 MIN")
            QuestionGuide(q.kind,
                when{feedback==true->MascotMood.CELEBRATE;feedback==false->MascotMood.THINKING;s.index%5==3->MascotMood.GOOFY;q.kind in listOf("morseListen","binary","number")->MascotMood.DETERMINED;else->MascotMood.HAPPY},
                when{feedback==true->MascotPose.JUMP;feedback==false->MascotPose.HUG;s.index%5==3->MascotPose.DANCE;else->MascotPose.POINT},
                when{feedback==true->listOf("Ton signal passe cinq sur cinq !","Pico sort sa danse de victoire.","Bien joué, on garde le rythme !")[s.index%3];feedback==false->"On prend le temps de comprendre, puis on réessaie.";else->listOf("Pico est avec toi. À toi de jouer !","Un défi à la fois, on capte les bons réflexes.","Branche tes neurones, la radio attend !")[s.index%3]})
            Text(q.prompt,fontSize=21.sp,lineHeight=28.sp,fontWeight=FontWeight.ExtraBold)
            if(bitmap!=null) Surface(onClick={enlarged=true},color=Color.White,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Image(bitmap,"Illustration de la question ${q.id}",Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height))
                    Text("Toucher pour agrandir",fontSize=10.sp,color=Muted)
                }
            }
            if(q.kind=="resistor") Resistor(q.bands)
            when(q.kind) {
                "flash" -> {
                    Surface(onClick={flipped=true},color=if(flipped)Mist else Color.White,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().heightIn(min=200.dp)) {
                        Box(Modifier.padding(18.dp),contentAlignment=Alignment.Center) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Icon(if(flipped)Icons.Rounded.CheckCircle else Icons.Rounded.TouchApp,null,tint=Teal,modifier=Modifier.size(32.dp))
                                Text(if(flipped)q.choices.firstOrNull().orEmpty() else "Retrouve la réponse dans ta tête, puis retourne la carte.",fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold)
                                if(flipped && q.explanation.isNotBlank())Text(q.explanation,fontSize=13.sp,color=Muted)
                            }
                        }
                    }
                }
                "number" -> {
                    OutlinedTextField(numeric,{if(feedback==null)numeric=it},label={Text("Ta réponse en ${q.unit}")},trailingIcon={Text(q.unit,Modifier.padding(end=12.dp),fontWeight=FontWeight.Bold)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=feedback==null)
                    if((q.value ?: 0.0)<0) OutlinedButton({numeric=if(numeric.startsWith("-"))numeric.drop(1) else "-$numeric"},enabled=feedback==null){Text("± Changer le signe")}
                    Text("La virgule ou le point sont acceptés. Pense aux unités.",fontSize=12.sp,color=Muted)
                }
                "frequency" -> {
                    Panel(color=Mist){Text("%.2f MHz".format(java.util.Locale.FRANCE,frequency),fontSize=38.sp,fontWeight=FontWeight.ExtraBold,color=Teal)
                        FrequencyDial(frequency)
                        Slider(frequency,{frequency=it},valueRange=143f..148f,steps=99,enabled=feedback==null)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("143 MHz",fontSize=11.sp);Text("148 MHz",fontSize=11.sp)}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton({frequency=(frequency-.05f).coerceAtLeast(143f)},enabled=feedback==null){Text("− 0,05")};OutlinedButton({frequency=(frequency+.05f).coerceAtMost(148f)},enabled=feedback==null){Text("+ 0,05")}}
                    }
                }
                "estimate" -> {
                    Panel(color=Gold.copy(alpha=.16f)) {
                        Text("${formatNumber(estimate.toDouble())} ${q.unit}",fontSize=32.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                        Slider(estimate,{estimate=it},valueRange=estimateMin..estimateMax,enabled=feedback==null)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${formatNumber(estimateMin.toDouble())} ${q.unit}",fontSize=11.sp);Text("${formatNumber(estimateMax.toDouble())} ${q.unit}",fontSize=11.sp)}
                        val step=q.bands.getOrNull(2)?.toFloatOrNull()?.takeIf{it>0f} ?: 1f
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton({estimate=(estimate-step).coerceAtLeast(estimateMin)},enabled=feedback==null){Text("− ${formatNumber(step.toDouble())}")};OutlinedButton({estimate=(estimate+step).coerceAtMost(estimateMax)},enabled=feedback==null){Text("+ ${formatNumber(step.toDouble())}")}}
                    }
                }
                "binary" -> BinarySwitches(binary,q.unit.toIntOrNull()?.coerceIn(1,8) ?: 4,feedback==null){binary=it}
                "morseEncode" -> MorseComposer(morse,{morse=it.take(180)},feedback==null)
                "morseListen" -> {
                    Panel(color=Color(0xFFE9EFFB)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Hearing,null,tint=Color(0xFF547FCD),modifier=Modifier.size(30.dp))
                            Text("Écoute les points courts et les traits longs.",fontSize=14.sp,fontWeight=FontWeight.Bold)
                        }
                        Action("Écouter le signal"){playMorse(q.bands.firstOrNull().orEmpty())}
                        Text("Tu peux le réécouter autant que nécessaire.",fontSize=11.sp,color=Muted)
                        if(feedback!=null)MorseSymbols(q.bands.firstOrNull().orEmpty())
                    }
                    q.choices.forEachIndexed{i,text->AnswerTile(text,choice==i,feedback==null,feedback!=null&&i==q.answer){choice=i}}
                }
                "truefalse" -> {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        q.choices.forEachIndexed{i,text->Surface(onClick={choice=i},enabled=feedback==null,modifier=Modifier.weight(1f).heightIn(min=104.dp),shape=RoundedCornerShape(18.dp),
                            color=if(feedback!=null&&i==q.answer)Mist else if(choice==i)Gold.copy(alpha=.35f)else Color.White,
                            border=BorderStroke(if(choice==i)2.dp else 1.dp,if(choice==i)Teal else Color(0xFFD5DEDA))) {
                            Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                val isTrue=text.trim().equals("Vrai",ignoreCase=true)
                                Icon(if(isTrue)Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,null,tint=if(isTrue)Teal else Coral,modifier=Modifier.size(30.dp))
                                Text(text,fontWeight=FontWeight.ExtraBold,fontSize=18.sp,color=Ink)
                            }
                        }}
                    }
                }
                "cloze" -> {
                    Panel(color=Purple.copy(alpha=.10f)) {
                        Text(if(choice<0)"La case vide attend le bon mot." else q.prompt.replace("___",q.choices.getOrNull(choice).orEmpty()),fontWeight=FontWeight.Bold,color=Purple,fontSize=18.sp,lineHeight=25.sp)
                    }
                    q.choices.forEachIndexed{i,text->AnswerTile(text,choice==i,feedback==null,feedback!=null&&i==q.answer){choice=i}}
                }
                "multiselect" -> {
                    Text("${selectedMany.size} réponse${if(selectedMany.size>1)"s"else""} cochée${if(selectedMany.size>1)"s"else""} · retouche pour décocher",fontSize=12.sp,color=Muted)
                    q.choices.forEachIndexed{i,text->val correctIndices=q.bands.mapNotNull{it.toIntOrNull()}.toSet()
                        Surface(onClick={selectedMany=if(i in selectedMany)selectedMany-i else selectedMany+i},enabled=feedback==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),
                            color=if(feedback!=null&&i in correctIndices)Mist else if(i in selectedMany)Gold.copy(alpha=.25f)else Color.White,
                            border=BorderStroke(if(i in selectedMany)2.dp else 1.dp,if(i in selectedMany)Teal else Color(0xFFD5DEDA))) {
                            Row(Modifier.padding(horizontal=12.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(if(i in selectedMany)Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,null,tint=Teal)
                                Text(text,fontSize=15.sp,lineHeight=21.sp,color=Ink,fontWeight=if(i in selectedMany)FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                "waveform" -> q.choices.forEachIndexed{i,text->
                    Surface(onClick={choice=i},enabled=feedback==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
                        color=if(feedback!=null&&i==q.answer)Mist else if(choice==i)Gold.copy(alpha=.25f)else Color.White,
                        border=BorderStroke(if(choice==i)2.dp else 1.dp,if(choice==i)Teal else Color(0xFFD5DEDA))) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {Text(text,fontSize=14.sp,fontWeight=FontWeight.Bold,color=Ink);WaveformPreview(q.bands.getOrNull(i) ?: "sine")}
                    }
                }
                "order","sort" -> {
                    Text("Retouche une étape choisie pour l'enlever.",fontSize=12.sp,color=Muted)
                    val shuffled=remember(key){q.choices.indices.shuffled(Random(q.id.hashCode()))}
                    if(ordered.isNotEmpty()) Panel(color=Mist){ordered.forEachIndexed{i,item->Text("${i+1}. ${q.choices[item]}",Modifier.fillMaxWidth().clickable(enabled=feedback==null){ordered=ordered-item},fontWeight=FontWeight.Bold,fontSize=15.sp)}}
                    shuffled.filter{it !in ordered}.forEach{index->AnswerTile(q.choices[index],false,feedback==null){ordered=ordered+index}}
                }
                "match" -> {
                    MatchBoard(q,key,matches,left,feedback==null,{i->left=i;matches=matches-i},{i->val selected=left;if(selected!=null){matches=matches.filterValues{it!=i}+(selected to i);left=null}})
                }
                else -> q.choices.forEachIndexed {i,text->
                    val good=feedback!=null && i==q.answer
                    AnswerTile(text,choice==i,feedback==null,good){choice=i}
                }
            }
            if(feedback!=null) {
                Panel(color=if(feedback)Mist else Color(0xFFFFE8E0)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(if(feedback)Icons.Rounded.CheckCircle else Icons.Rounded.Lightbulb,null,tint=if(feedback)Teal else Color(0xFFBA5546));Spacer(Modifier.width(10.dp));Text(if(feedback)"Signal reçu !" else "Une occasion de retenir",fontSize=20.sp,fontWeight=FontWeight.Bold)}
                    if(!feedback)Text(solution(q),fontWeight=FontWeight.Bold,fontSize=16.sp,lineHeight=23.sp)
                    if(!feedback&&q.kind=="morseEncode")MorseSymbols(q.bands.firstOrNull().orEmpty())
                    Text(q.explanation.ifBlank {"La banque Exam1 ne fournit pas de commentaire pour cette question. La réponse de référence est conservée ci-dessus."},fontSize=14.sp,lineHeight=21.sp)
                    if(q.source.startsWith("http"))TextButton({openLink(context,q.source)}){Text("Consulter la question source")}
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Surface(color=Cream,shadowElevation=5.dp) {
            Column(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                if(q.kind=="flash") {
                    if(!flipped)Action("Retourner la carte"){flipped=true}
                    else {
                        Text("Comment était le rappel ?",fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf(2 to "À revoir",3 to "Difficile",4 to "Bien",5 to "Facile").forEach{(quality,label)->
                                OutlinedButton({model.answer(quality>=3,quality);model.next()},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=12.dp)){Text(label,fontSize=11.sp)}
                            }
                        }
                    }
                } else if(feedback==null) {
                    Action(if(s.exam)"Répondre et continuer" else "Vérifier",enabled=canAnswer){focus.clearFocus();model.answer(check())}
                    if(s.exam)TextButton({model.answer(false,omitted=true)},Modifier.fillMaxWidth()){Text("Laisser sans réponse")}
                } else Action("Continuer"){model.next()}
            }
        }
    }
    if(quit)AlertDialog(onDismissRequest={quit=false},title={Text("Faire une pause ?")},text={Text("Ton XP et tes révisions sont enregistrés. Pour valider une leçon, vise au moins 80 % dès le premier essai et corrige les erreurs restantes.")},confirmButton={TextButton({model.leaveSession();quit=false}){Text("Quitter")}},dismissButton={TextButton({quit=false}){Text("Revenir au défi")}})
    if(enlarged&&bitmap!=null) Dialog(onDismissRequest={enlarged=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp),shape=RoundedCornerShape(20.dp),color=Color.White) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                var zoom by remember{mutableFloatStateOf(1f)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Illustration Exam1",fontWeight=FontWeight.Bold);IconButton({enlarged=false}){Icon(Icons.Rounded.Close,"Fermer")}}
                Slider(zoom,{zoom=it},valueRange=1f..3f)
                Box(Modifier.heightIn(max=500.dp).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                    Image(bitmap,"Illustration agrandie",Modifier.width((330*zoom).dp).aspectRatio(bitmap.width.toFloat()/bitmap.height))
                }
            }
        }
    }
}

fun solution(q:Question):String=when(q.kind){
    "number","frequency","estimate"->"Réponse : ${formatNumber(q.value ?: 0.0)} ${q.unit}"
    "binary"->"Réponse : ${(q.value ?: 0.0).toInt().toString(2).padStart(q.unit.toIntOrNull() ?: 4,'0')} en binaire = ${(q.value ?: 0.0).toInt()} en décimal"
    "morseEncode"->"Réponse : ${normalizeMorse(q.bands.firstOrNull().orEmpty()).replace(".","●").replace("-","━")}"
    "multiselect"->"Réponses : "+q.bands.mapNotNull{it.toIntOrNull()?.let(q.choices::getOrNull)}.joinToString(" · ")
    "match"->q.pairs.joinToString("\n"){"${it.left} → ${it.right}"}
    "order","sort"->q.choices.mapIndexed{i,t->"${i+1}. $t"}.joinToString("\n")
    else->"Réponse : ${q.choices.getOrNull(q.answer).orEmpty()}"
}
@Composable fun AnswerTile(text:String,selected:Boolean,enabled:Boolean,good:Boolean=false,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),
        color=if(good)Mist else if(selected)Color(0xFFFFE9CC) else Color.White,
        border=BorderStroke(if(selected||good)2.dp else 1.dp,if(good)Teal else if(selected)Color(0xFFE5A246) else Color(0xFFD5DEDA))) {
        Text(text,Modifier.padding(horizontal=14.dp,vertical=12.dp).heightIn(min=24.dp),fontSize=15.sp,lineHeight=21.sp,fontWeight=if(selected||good)FontWeight.Bold else FontWeight.Medium,color=Ink)
    }
}
@Composable fun Resistor(bands:List<String>) {
    val colors=mapOf("noir" to Color.Black,"brun" to Color(0xFF865239),"marron" to Color(0xFF865239),"rouge" to Color(0xFFE54546),"orange" to Color(0xFFFF953E),"jaune" to Color(0xFFF7D345),"vert" to Color(0xFF43A77B),"bleu" to Color(0xFF4276CA),"violet" to Purple,"gris" to Color.Gray,"blanc" to Color.White,"or" to Gold,"argent" to Color.LightGray)
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(115.dp)) {
            val w=size.width;val h=size.height
            drawLine(Color.Gray,Offset(0f,h/2),Offset(w,h/2),8f,StrokeCap.Round)
            drawRoundRect(Color(0xFFF0D3A5),Offset(w*.14f,h*.2f),Size(w*.72f,h*.6f),CornerRadius(25f))
            bands.forEachIndexed{i,color->val x=if(i==bands.size-1)w*.74f else w*(.25f+i*.12f);drawRect(colors[color.lowercase()]?:Teal,Offset(x,h*.2f),Size(w*.045f,h*.6f))}
        }
        Text(bands.joinToString(" · "),fontSize=12.sp,color=Muted)
    }
}
@Composable fun FrequencyDial(frequency:Float) {
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        repeat(51){i->val x=size.width*i/50;drawLine(Teal.copy(alpha=.45f),Offset(x,if(i%10==0)5f else 25f),Offset(x,55f),if(i%10==0)3f else 1f)}
        val x=(frequency-143)/5*size.width
        drawLine(Coral,Offset(x,0f),Offset(x,size.height),5f,StrokeCap.Round)
    }
}
@Composable fun ResultsScreen(model:AppModel,s:Session) {
    val context=LocalContext.current
    val passed=if(s.exam)LearningRules.examPassed(s.regulationScore,s.techniqueScore) else if(s.lessonId!=null)s.lessonPassed else s.unresolved.isEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Pico(Modifier.size(132.dp),happy=passed,mood=if(passed)MascotMood.CELEBRATE else MascotMood.DETERMINED,pose=if(passed)MascotPose.DANCE else MascotPose.HUG)
        BigTitle(if(passed)if(s.exam)"Prêt pour le grand contact !" else "Bien joué, Hamigo !" else "Ton signal progresse",if(s.exam)"Examen blanc terminé" else "Chaque rappel renforce ta mémoire.")
        Panel(color=if(passed)Mist else Color(0xFFFFE8E0)) {
            if(s.exam) {Text("Réglementation : ${s.regulationScore} / 20\nTechnique : ${s.techniqueScore} / 20",fontSize=21.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold);Text(if(passed)"Les deux seuils de 10/20 sont atteints." else "Il faut 10/20 dans chacune des deux parties.",color=Muted);if(s.unanswered>0)Text("${s.unanswered} questions sans réponse.",fontSize=13.sp)}
            else {Text("+ ${s.gain} XP",fontSize=35.sp,fontWeight=FontWeight.ExtraBold,color=Teal);Text("${s.correct} réponses réussies sur ${s.questions.size} essais · ${model.progress.streak} jours de série",fontSize=13.sp,color=Muted)}
            if(!s.exam&&s.lessonId!=null) {
                Text("${s.firstCorrect}/${s.firstCount} réponses justes au premier essai",fontWeight=FontWeight.Bold)
                LinearProgressIndicator(progress={s.firstCorrect.toFloat()/s.firstCount.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().height(8.dp),color=if(passed)Teal else Coral,trackColor=Color.White)
                Text(if(passed)"Leçon validée !" else "Il faut au moins 80 % au premier essai et aucune erreur restante. Cette leçon reste à consolider.",fontSize=13.sp,lineHeight=19.sp,color=Muted)
            }
        }
        Action("Revenir au parcours"){model.leaveSession();model.route="path"}
        if(!s.exam&&s.lessonId!=null&&!passed)model.content?.lessons?.firstOrNull{it.id==s.lessonId}?.let{lesson->Action("Reprendre le cours"){model.startLesson(lesson)}}
        if(s.missed.isNotEmpty())OutlinedButton({model.startQuestions("On consolide le signal",s.missed.values.toList())},Modifier.fillMaxWidth()){Text("Revoir les ${s.missed.size} questions manquées")}
        OutlinedButton({NativeShare.progressImage(context,model.progress.snapshot())},Modifier.fillMaxWidth()){Text("Partager ma progression")}
        if(s.exam) s.missed.values.forEach{q->Panel {Text(q.prompt,fontWeight=FontWeight.Bold,fontSize=15.sp);Text(solution(q),color=Teal,fontSize=14.sp);Text(q.explanation,fontSize=13.sp,color=Muted);if(q.source.startsWith("http"))TextButton({openLink(context,q.source)}){Text("Voir la source et l'illustration")}}}
    }
}

private val audioScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
private var audioJob:Job?=null
fun stopMorse() { audioJob?.cancel();audioJob=null }
fun playMorse(code:String) {
    audioJob?.cancel()
    audioJob=audioScope.launch {
        val message=normalizeMorse(code).take(240)
        if(message.isBlank())return@launch
        val rate=16000;val dot=rate*90/1000
        val samples=ArrayList<Short>()
        val words=message.split('/').map{it.trim().split(Regex("\\s+")).filter{part->part.isNotBlank()}}.filter{it.isNotEmpty()}
        words.forEachIndexed{wi,letters->letters.forEachIndexed{li,symbols->symbols.forEachIndexed{si,c->
            if(c=='.'||c=='-') {
                val duration=dot*if(c=='.')1 else 3
                repeat(duration){i->val envelope=minOf(1.0,i/100.0,(duration-i)/100.0);samples.add((sin(2*Math.PI*700*i/rate)*10000*envelope).toInt().toShort())}
                val gap=when{si<symbols.lastIndex->1;li<letters.lastIndex->3;wi<words.lastIndex->7;else->1}
                repeat(dot*gap){samples.add(0)}
            }
        }}
        }
        if(samples.isEmpty())return@launch
        val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(samples.size*2).setTransferMode(AudioTrack.MODE_STATIC).build()
        try {val pcm=samples.toShortArray();track.write(pcm,0,pcm.size);track.play();delay(pcm.size*1000L/rate+100)} finally {track.stop();track.release()}
    }
}
