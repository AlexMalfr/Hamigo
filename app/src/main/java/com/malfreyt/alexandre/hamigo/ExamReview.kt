package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable fun rememberExamClock(model:AppModel,s:Session):Long {
    var timer by remember(s) {mutableLongStateOf(s.examTimeRemaining)}
    LaunchedEffect(s,s.examPart,s.examIntroPending) {
        timer=s.examTimeRemaining
        if(s.exam && !s.examIntroPending) while(!s.done) {
            timer=s.examTimeRemaining
            if(timer<=0) {model.timeoutExamPart();break}
            delay(1000)
        }
    }
    return timer
}

fun examDuration(milliseconds:Long):String {
    val seconds=milliseconds.coerceAtLeast(0)/1000
    return "%02d:%02d".format(seconds/60,seconds%60)
}

@Composable fun ExamIntroduction(model:AppModel,s:Session) {
    val technique=s.examPart==1
    Column(Modifier.fillMaxSize().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center) {
        Eyebrow("ÉPREUVE ${s.examPart+1} / 2",if(technique)Teal else Purple)
        Spacer(Modifier.height(18.dp))
        Pico(Modifier.size(174.dp),mood=if(technique)MascotMood.DETERMINED else MascotMood.HAPPY,pose=if(technique)MascotPose.POINT else MascotPose.WAVE)
        Spacer(Modifier.height(10.dp))
        Surface(color=Color.White,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Mist)) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(if(technique)"On branche la technique !" else "Prêt à ouvrir la fréquence ?",fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                Text(if(technique)"Une nouvelle épreuve, un nouveau départ. Respire, prends ton temps et vérifie tes calculs." else "Lis bien chaque question. Tu pourras revenir sur tes réponses avant de finaliser cette épreuve.",fontSize=15.sp,lineHeight=22.sp,color=Muted)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(s.examPartLabel,fontSize=26.sp,fontWeight=FontWeight.ExtraBold,color=if(technique)Teal else Purple)
        Text("${s.examPartEnd-s.examPartStart} questions · ${s.examMinutes} minutes · objectif 10/20",fontSize=14.sp,color=Muted,modifier=Modifier.padding(top=5.dp,bottom=20.dp))
        Action("Commencer ${if(technique)"la technique" else "la réglementation"}"){model.beginExamPart()}
        TextButton(feedbackClick {model.leaveSession();model.route="practice"}) {Text("Revenir aux défis")}
    }
}

@Composable fun ExamPartReview(model:AppModel,s:Session,timer:Long) {
    var confirm by remember(s.examPart) {mutableStateOf(false)}
    val answered=(s.examPartStart until s.examPartEnd).count {s.responses[it]?.omitted==false}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Relire ${s.examPartLabel.lowercase()}",fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=Ink,modifier=Modifier.weight(1f))
            Text(examDuration(timer),fontWeight=FontWeight.Bold,color=if(timer<60_000)Coral else Teal)
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Pico(Modifier.size(88.dp),mood=MascotMood.THINKING,pose=MascotPose.POINT)
            Text("Encore un coup d’œil ? Touche un numéro pour modifier ta réponse. Le chrono continue.",fontSize=14.sp,lineHeight=21.sp,color=Muted,modifier=Modifier.weight(1f))
        }
        Text("$answered/${s.examPartEnd-s.examPartStart} réponses enregistrées",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Teal)
        (s.examPartStart until s.examPartEnd).toList().chunked(5).forEach {row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                row.forEach {index ->
                    val response=s.responses[index]
                    val selected=response!=null && !response.omitted
                    Surface(onClick=feedbackClick {model.revisitExamQuestion(index)},color=if(selected)Mist else Color.White,
                        shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,if(selected)Teal.copy(alpha=.3f) else Color(0xFFD5DEDA)),
                        modifier=Modifier.weight(1f).height(60.dp)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text("${index+1}",fontWeight=FontWeight.ExtraBold,color=Ink)
                            Text(if(selected)listOf("A","B","C","D").getOrNull(response!!.choiceIndex) ?: "✓" else "—",fontSize=12.sp,color=if(selected)Teal else Muted)
                        }
                    }
                }
                repeat(5-row.size) {Spacer(Modifier.weight(1f))}
            }
        }
        Text("Vert : réponse enregistrée · — : sans réponse",fontSize=12.sp,color=Muted)
        Spacer(Modifier.height(5.dp))
        Action("Finaliser ${if(s.examPart==0)"la réglementation" else "la technique"}"){confirm=true}
        TextButton(feedbackClick {model.revisitExamQuestion(s.examPartStart)},Modifier.fillMaxWidth()) {Text("Relire depuis le début")}
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Valider cette épreuve ?")},
        text={Text("${s.examPartEnd-s.examPartStart-answered} question(s) sans réponse. Les réponses seront ensuite définitives.")},
        confirmButton={TextButton(feedbackClick {confirm=false;model.finishExamPart()}) {Text("Finaliser")}},
        dismissButton={TextButton(feedbackClick {confirm=false}) {Text("Continuer à relire")}})
}
