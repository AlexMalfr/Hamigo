package com.malfreyt.alexandre.hamigo

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun MorseComposerInput(code:String,enabled:Boolean=true,settingsOverride:MorseInputSettings?=null,
    preview:(@Composable ()->Unit)?=null,belowPreview:(@Composable ()->Unit)?=null,onChange:(String)->Unit) {
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
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                if(preview!=null)preview()
                else if(code.isBlank())Text("Ton signal",fontSize=13.sp,lineHeight=18.sp,color=Muted)
                else MorseVisual(code,compact=true)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(enabled=enabled&&code.isNotEmpty(),role=Role.Button,
                onClick=feedbackClick {release=0;emit(code.dropLast(1))},onLongClickLabel="Tout effacer",
                onLongClick=feedbackClick {release=0;emit("")}),contentAlignment=Alignment.Center) {Icon(Icons.Rounded.Backspace,"Effacer le dernier symbole",tint=if(enabled&&code.isNotEmpty())Ink else Muted.copy(alpha=.4f))}
        }
        belowPreview?.invoke()
        MorseSignalInput(enabled,onPressChanged={holding=it},settingsOverride=settings) {symbol->
            feedback?.event(if(symbol=='.')FeedbackCue.MORSE_DOT else FeedbackCue.MORSE_DASH)
            expected=(latest+symbol).take(240);emit(expected);release=SystemClock.uptimeMillis()
        }
        if(!settings.autoSpaces)Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()) {
                OutlinedButton(feedbackClick {release=0;emit(code.trimEnd()+" ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(4.dp)){Text("Lettre suivante",fontSize=11.sp)}
                OutlinedButton(feedbackClick {release=0;emit(code.trimEnd().removeSuffix("/").trimEnd()+" / ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(4.dp)){Text("Mot suivant",fontSize=11.sp)}
        }
    }
}
