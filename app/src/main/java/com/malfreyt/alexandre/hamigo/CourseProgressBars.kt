package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable internal fun CourseProgressBars(completion:PathCompletion) {
    Panel(modifier=Modifier.testTag("profile-course-progress")) {
        Text("Ton parcours",fontSize=20.sp,fontWeight=FontWeight.Bold)
        CourseProgressBar("Préparer le certificat",completion.certificate,Teal,"certificate")
        CourseProgressBar("Explorer la radio",completion.exploration,Purple,"exploration")
        Text("Selon les cours validés ou ignorés pour chaque objectif.",fontSize=12.sp,lineHeight=18.sp,color=Muted)
    }
}

@Composable private fun CourseProgressBar(label:String,progress:Float,color:Color,tag:String) {
    val value=progress.coerceIn(0f,1f)
    Column(Modifier.fillMaxWidth().testTag("course-progress-$tag"),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text("${(value*100).roundToInt()} %",color=color,fontSize=14.sp,fontWeight=FontWeight.ExtraBold)
        }
        LinearProgressIndicator(progress={value},modifier=Modifier.fillMaxWidth().height(8.dp)
            .semantics {contentDescription=label},color=color,trackColor=color.copy(alpha=.12f))
    }
}

/** The explanation lives next to the course, without adding a permanent legend to Parcours. */
@Composable internal fun CoursePurposeBadge(lesson:Lesson,compact:Boolean=false) {
    if(lesson.certificateWeight<=0&&lesson.explorationWeight<=0)return
    val certificate=lesson.certificateWeight>0
    val label=if(certificate)"Utile au certificat" else "Explorer la radio"
    val color=if(certificate)Teal else Purple
    var help by remember(lesson.id) {mutableStateOf(false)}
    if(compact) {
        IconButton(feedbackClick {help=true},Modifier.size(40.dp).testTag("course-purpose-${lesson.id}")) {
            Icon(if(certificate)Icons.Rounded.School else Icons.Rounded.Explore,"$label. En savoir plus",Modifier.size(17.dp),tint=color)
        }
    } else {
        Surface(onClick=feedbackClick {help=true},shape=RoundedCornerShape(16.dp),color=color.copy(alpha=.09f),
            modifier=Modifier.testTag("course-purpose-${lesson.id}").semantics {contentDescription="$label. En savoir plus"}) {
            Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Icon(if(certificate)Icons.Rounded.School else Icons.Rounded.Explore,null,Modifier.size(17.dp),tint=color)
                Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold,color=color)
                Icon(Icons.Rounded.Info,null,Modifier.size(14.dp),tint=color.copy(alpha=.7f))
            }
        }
    }
    if(help)AlertDialog(onDismissRequest={help=false},icon={Icon(if(certificate)Icons.Rounded.School else Icons.Rounded.Explore,null,tint=color)},
        title={Text(label)},text={Text(if(certificate)
            "Ce cours travaille des notions utiles à l’épreuve française. Tu peux donner la priorité à ces cours pour préparer le certificat, tout en gardant un parcours libre."
            else "Ce cours aide à découvrir et pratiquer la radio au-delà du programme du certificat. Tu peux l’explorer quand tu veux, puis revenir à la préparation de l’examen.")},
        confirmButton={TextButton(feedbackClick {help=false}){Text("Compris")}})
}
