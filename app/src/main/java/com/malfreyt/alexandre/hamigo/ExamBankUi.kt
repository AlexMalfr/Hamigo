package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun examBankVersionLabel(version:String)=runCatching {
    java.time.OffsetDateTime.parse(version).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm",java.util.Locale.FRANCE))
}.getOrDefault(version)

@Composable internal fun ExamBankLoading(model:AppModel) {
    val state=model.examBank
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Banque Exam’1",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=15.sp)
        if(state.updating&&state.percent!=null)Text("${state.percent} %",color=Teal,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("exam-bank-percent"))
    }
    if(state.updating) {
        if(state.percent==null)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("exam-bank-progress"))
        else LinearProgressIndicator(progress={state.percent/100f},modifier=Modifier.fillMaxWidth().testTag("exam-bank-progress"))
    }
    Text(when {
        state.error!=null->state.error
        state.ready->"Préparation des questions…"
        state.updating->"Téléchargement en cours. Les cours et révisions restent disponibles."
        else->"Téléchargement dès qu’Internet est disponible. Les cours et révisions restent disponibles."
    },fontSize=12.sp,color=Muted,lineHeight=17.sp)
    if(!state.updating)TextButton(feedbackClick {model.checkExamBank()},contentPadding=PaddingValues(0.dp)) {Text("Réessayer")}
}

@Composable internal fun ExamBankSettings(model:AppModel) {
    val state=model.examBank
    Panel(Modifier.testTag("exam-bank-settings")) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Banque Exam’1",fontWeight=FontWeight.Bold,fontSize=15.sp)
                Text(state.snapshot?.let {"Version : ${examBankVersionLabel(it.version)}"} ?: "Pas encore téléchargée",fontSize=12.sp,color=Muted,lineHeight=17.sp,modifier=Modifier.testTag("exam-bank-version"))
            }
            IconButton(feedbackClick {model.checkExamBank()},enabled=!state.updating&&!model.diagnosticModel,modifier=Modifier.testTag("exam-bank-check")) {
                Icon(Icons.Rounded.Refresh,"Vérifier les mises à jour",tint=Teal)
            }
        }
        Text("Vérification quotidienne en arrière-plan. Disponible hors ligne après téléchargement.",fontSize=11.sp,lineHeight=16.sp,color=Muted)
        if(state.updating) {
            if(state.percent==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress={state.percent/100f},modifier=Modifier.fillMaxWidth())
            Text(state.percent?.let {"Téléchargement · $it %"} ?: "Vérification en cours…",fontSize=12.sp,color=Teal)
        } else if(state.error!=null)Text(state.error,fontSize=12.sp,lineHeight=17.sp,color=Muted)
        else if(state.checkedAt>0)Text("Vérifié le "+java.text.SimpleDateFormat("dd/MM/yyyy 'à' HH:mm",java.util.Locale.FRANCE).format(java.util.Date(state.checkedAt)),fontSize=11.sp,color=Muted)
    }
}
