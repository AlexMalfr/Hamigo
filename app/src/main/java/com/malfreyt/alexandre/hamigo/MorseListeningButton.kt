package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun MorseListeningButton(code:String,outlined:Boolean=false) {
    val normalized=remember(code){normalizeMorse(code)}
    val playing=MorsePlayback.code==normalized
    val label=if(playing)"Arrêter le signal" else "Écouter le signal"
    val toggle={if(playing)stopMorse() else playMorse(code)}
    if(outlined)OutlinedButton(feedbackClick(toggle),Modifier.fillMaxWidth()) {
        Icon(if(playing)Icons.Rounded.Stop else Icons.Rounded.Hearing,null);Spacer(Modifier.width(8.dp));Text(label)
    } else Action(label){toggle()}
    if(normalized.length>240) {
        val seconds=remember(normalized){MorseSignalAudio.durationMs(normalized)/1000}
        Text("Échange simulé · environ $seconds s · réécoute possible",fontSize=12.sp,color=Muted)
    }
}
