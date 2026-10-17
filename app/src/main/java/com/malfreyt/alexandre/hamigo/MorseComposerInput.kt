package com.malfreyt.alexandre.hamigo

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** One timing owner for questions, translator and settings; pauses stop on press, edit or exit. */
@Composable internal fun MorseComposerInput(code:String,enabled:Boolean=true,settingsOverride:MorseInputSettings?=null,onChange:(String)->Unit) {
    val settings=settingsOverride ?: rememberMorseInputSettings()
    val latest by rememberUpdatedState(code)
    val emit by rememberUpdatedState(onChange)
    var release by remember {mutableLongStateOf(0L)}
    var holding by remember {mutableStateOf(false)}
    var expected by remember {mutableStateOf("")}
    val feedback=LocalAppFeedback.current
    LaunchedEffect(release,holding,enabled,settings.autoSpaces,settings.thresholdMs,code) {
        if(settings.autoSpaces&&enabled&&!holding&&release>0L&&code.isNotBlank()&&(code==expected||code==expected+" ")) {
            delay((release+settings.letterPauseMs-SystemClock.uptimeMillis()).coerceAtLeast(0))
            if(latest==expected)emit(expected+" ")
            delay((release+settings.wordPauseMs-SystemClock.uptimeMillis()).coerceAtLeast(0))
            if(latest==expected+" ")emit(expected.trimEnd()+" / ")
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        MorseSignalInput(enabled,onPressChanged={holding=it},settingsOverride=settings) {symbol->
            feedback?.event(if(symbol=='.')FeedbackCue.MORSE_DOT else FeedbackCue.MORSE_DASH)
            expected=(latest+symbol).take(240);emit(expected);release=SystemClock.uptimeMillis()
        }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()) {
            if(!settings.autoSpaces) {
                OutlinedButton(feedbackClick {release=0;emit(code.trimEnd()+" ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(4.dp)){Text("Lettre suivante",fontSize=11.sp)}
                OutlinedButton(feedbackClick {release=0;emit(code.trimEnd().removeSuffix("/").trimEnd()+" / ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(4.dp)){Text("Mot suivant",fontSize=11.sp)}
            } else Text("Les silences séparent les lettres et les mots",Modifier.weight(1f).padding(vertical=12.dp),fontSize=11.sp,color=Muted)
            IconButton(feedbackClick {release=0;emit(code.dropLast(1))},enabled=enabled&&code.isNotEmpty()){Icon(Icons.Rounded.Backspace,"Effacer le dernier symbole")}
        }
    }
}
