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
    var quit by remember{mutableStateOf(false)}
    var enlarged by remember{mutableStateOf(false)}
    var timer by remember{mutableLongStateOf(s.examTimeRemaining)}
    val feedback=s.feedback
    LaunchedEffect(key){scroll.scrollTo(0);focus.clearFocus()}
    val bitmap=remember(q.image){q.image?.let{path->runCatching{context.assets.open(path).use{BitmapFactory.decodeStream(it)?.asImageBitmap()}}.getOrNull()}}
    LaunchedEffect(s,s.index/20) {
        if(s.exam) while(!s.done) {timer=s.examTimeRemaining;if(timer<=0){model.timeoutExamPart();break};delay(1000)}
    }
    val canAnswer=when(q.kind) {
        "number"->numeric.isNotBlank();"order"->ordered.size==q.choices.size
        "match"->matches.size==q.pairs.size;"frequency"->true;else->choice>=0
    }
    fun check()=when(q.kind) {
        "number"->LearningRules.numericCorrect(numeric,q.value ?: 0.0,q.tolerance)
        "frequency"->kotlin.math.abs(frequency-(q.value ?: 0.0)) <= q.tolerance
        "order"->ordered==q.choices.indices.toList()
        "match"->matches.all{it.key==it.value}
        else->choice==q.answer
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton({quit=true}){Icon(Icons.Rounded.Close,"Quitter la séance")}
            Column(Modifier.weight(1f)) {Text(s.title,maxLines=1,fontSize=13.sp,fontWeight=FontWeight.Bold,color=Muted);Spacer(Modifier.height(7.dp));LinearProgressIndicator(progress={(s.index.toFloat()/s.questions.size).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(8.dp),color=Teal,trackColor=Mist)}
            Spacer(Modifier.width(12.dp));Text(if(s.exam)"%02d:%02d".format(timer/60000,(timer/1000)%60) else "${s.index+1}/${s.questions.size}",fontSize=13.sp,fontWeight=FontWeight.Bold,color=if(s.exam&&timer<60000)Coral else Teal)
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Eyebrow(if(s.exam)if(s.index<20)"RÉGLEMENTATION · 15 MIN" else "TECHNIQUE · 30 MIN" else when(q.kind){"match"->"LES BONNES CONNEXIONS";"order"->"REMETS LE SIGNAL EN ORDRE";"number"->"À TOI DE CALCULER";"resistor"->"DÉCODE LES COULEURS";"flash"->"FLASHCARD · RAPPEL ACTIF";"frequency"->"ACCORDE LA FRÉQUENCE";else->"CAPTE LA BONNE RÉPONSE"})
            Text(q.prompt,fontSize=23.sp,lineHeight=31.sp,fontWeight=FontWeight.ExtraBold)
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
                        Box(Modifier.padding(24.dp),contentAlignment=Alignment.Center) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
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
                "order" -> {
                    Text("Touche les étapes dans l'ordre. Retouche une étape choisie pour l'enlever.",fontSize=13.sp,color=Muted)
                    val shuffled=remember(key){q.choices.indices.shuffled(Random(q.id.hashCode()))}
                    if(ordered.isNotEmpty()) Panel(color=Mist){ordered.forEachIndexed{i,item->Text("${i+1}. ${q.choices[item]}",Modifier.fillMaxWidth().clickable(enabled=feedback==null){ordered=ordered-item},fontWeight=FontWeight.Bold,fontSize=15.sp)}}
                    shuffled.filter{it !in ordered}.forEach{index->AnswerTile(q.choices[index],false,feedback==null){ordered=ordered+index}}
                }
                "match" -> {
                    Text("Choisis un élément à gauche, puis son partenaire à droite. Retouche un lien pour le modifier.",fontSize=13.sp,color=Muted)
                    val shuffled=remember(key){q.pairs.indices.shuffled(Random(q.id.hashCode()))}
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {q.pairs.forEachIndexed{i,pair->
                            AnswerTile((if(i in matches)"${i+1} · " else "")+pair.left,left==i || i in matches,feedback==null){left=i;matches=matches-i}
                        }}
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {shuffled.forEach{i->
                            val link=matches.entries.firstOrNull{it.value==i}?.key
                            AnswerTile((if(link!=null)"${link+1} · " else "")+q.pairs[i].right,link!=null,feedback==null && left!=null){
                                val selected=left
                                if(selected!=null){matches=matches.filterValues{it!=i}+(selected to i);left=null}
                            }
                        }}
                    }
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
                    Text(q.explanation.ifBlank {"La banque Exam1 ne fournit pas de commentaire pour cette question. La réponse de référence est conservée ci-dessus."},fontSize=15.sp,lineHeight=23.sp)
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
    if(quit)AlertDialog(onDismissRequest={quit=false},title={Text("Faire une pause ?")},text={Text("Ton XP et tes révisions sont enregistrés. La leçon se valide quand tous ses défis sont compris.")},confirmButton={TextButton({model.leaveSession();quit=false}){Text("Quitter")}},dismissButton={TextButton({quit=false}){Text("Revenir au défi")}})
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
    "number","frequency"->"Réponse : ${formatNumber(q.value ?: 0.0)} ${q.unit}"
    "match"->q.pairs.joinToString("\n"){"${it.left} → ${it.right}"}
    "order"->q.choices.mapIndexed{i,t->"${i+1}. $t"}.joinToString("\n")
    else->"Réponse : ${q.choices.getOrNull(q.answer).orEmpty()}"
}
@Composable fun AnswerTile(text:String,selected:Boolean,enabled:Boolean,good:Boolean=false,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),
        color=if(good)Mist else if(selected)Color(0xFFFFE9CC) else Color.White,
        border=BorderStroke(if(selected||good)2.dp else 1.dp,if(good)Teal else if(selected)Color(0xFFE5A246) else Color(0xFFD5DEDA))) {
        Text(text,Modifier.padding(horizontal=16.dp,vertical=18.dp),fontSize=16.sp,lineHeight=23.sp,fontWeight=if(selected||good)FontWeight.Bold else FontWeight.Medium,color=Ink)
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
    val passed=if(s.exam)LearningRules.examPassed(s.regulationScore,s.techniqueScore) else s.unresolved.isEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(12.dp));Pico(Modifier.size(175.dp),happy=passed)
        BigTitle(if(passed)if(s.exam)"Prêt pour le grand contact !" else "Bien joué, Hamigo !" else "Ton signal progresse",if(s.exam)"Examen blanc terminé" else "Chaque rappel renforce ta mémoire.")
        Panel(color=if(passed)Mist else Color(0xFFFFE8E0)) {
            if(s.exam) {Text("Réglementation : ${s.regulationScore} / 20\nTechnique : ${s.techniqueScore} / 20",fontSize=21.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold);Text(if(passed)"Les deux seuils de 10/20 sont atteints." else "Il faut 10/20 dans chacune des deux parties.",color=Muted);if(s.unanswered>0)Text("${s.unanswered} questions sans réponse.",fontSize=13.sp)}
            else {Text("+ ${s.gain} XP",fontSize=35.sp,fontWeight=FontWeight.ExtraBold,color=Teal);Text("${s.correct} réponses réussies sur ${s.questions.size} essais · ${model.progress.streak} jours de série",fontSize=13.sp,color=Muted)}
            if(!s.exam&&s.lessonId!=null)Text(if(passed)"Leçon validée !" else "Revois les erreurs pour valider cette leçon.",fontWeight=FontWeight.Bold)
        }
        Action("Revenir au parcours"){model.leaveSession();model.route="path"}
        if(s.missed.isNotEmpty())OutlinedButton({model.startQuestions("On consolide le signal",s.missed.values.toList(),s.lessonId)},Modifier.fillMaxWidth()){Text("Revoir les ${s.missed.size} questions manquées")}
        OutlinedButton({NativeShare.progressImage(context,model.progress.snapshot())},Modifier.fillMaxWidth()){Text("Partager ma progression")}
        if(s.exam) s.missed.values.forEach{q->Panel {Text(q.prompt,fontWeight=FontWeight.Bold,fontSize=15.sp);Text(solution(q),color=Teal,fontSize=14.sp);Text(q.explanation,fontSize=13.sp,color=Muted);if(q.source.startsWith("http"))TextButton({openLink(context,q.source)}){Text("Voir la source et l'illustration")}}}
    }
}

private val audioScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
private var audioJob:Job?=null
fun playMorse(code:String) {
    audioJob?.cancel()
    audioJob=audioScope.launch {
        val symbols=code.filter{it in ".-·−–•"}.take(30)
        if(symbols.isBlank())return@launch
        val rate=16000;val dot=rate*90/1000
        val samples=ArrayList<Short>()
        symbols.forEach{c->val duration=dot*if(c in ".·•")1 else 3
            repeat(duration){i->val envelope=minOf(1.0,i/100.0,(duration-i)/100.0);samples.add((sin(2*Math.PI*700*i/rate)*10000*envelope).toInt().toShort())}
            repeat(dot){samples.add(0)}
        }
        val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(samples.size*2).setTransferMode(AudioTrack.MODE_STATIC).build()
        try {val pcm=samples.toShortArray();track.write(pcm,0,pcm.size);track.play();delay(pcm.size*1000L/rate+100)} finally {track.stop();track.release()}
    }
}
