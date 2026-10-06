package com.malfreyt.alexandre.hamigo

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Preview only: timing never rewrites a course answer or an existing player's progression. */
internal data class MorsePreviewState(val code: String="", val releasedAt: Long?=null) {
    private fun separator(pause: Long, settings: MorseInputSettings)=when {
        pause>=settings.wordPauseMs -> " / "
        pause>=settings.letterPauseMs -> " "
        else -> ""
    }
    fun record(symbol: Char, downAt: Long, upAt: Long, settings: MorseInputSettings): MorsePreviewState {
        val base=code.trimEnd(' ','/')
        val gap=if(base.isEmpty() || releasedAt==null)"" else separator((downAt-releasedAt).coerceAtLeast(0),settings)
        return MorsePreviewState((base+gap+symbol).takeLast(128).trimStart(' ','/'),upAt)
    }
    fun pause(duration: Long, settings: MorseInputSettings): MorsePreviewState {
        val base=code.trimEnd(' ','/')
        return if(base.isEmpty())this else copy(code=base+separator(duration,settings))
    }
}

@Composable internal fun MorseSettingsPreview(settings: MorseInputSettings) {
    var example by remember { mutableStateOf(MorsePreviewState()) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(settings.singleKey,settings.thresholdMs) { example=MorsePreviewState();holding=false }
    LaunchedEffect(example.releasedAt,holding,settings.singleKey,settings.thresholdMs) {
        val release=example.releasedAt
        if(settings.singleKey && !holding && release!=null) {
            delay((settings.letterPauseMs-(SystemClock.uptimeMillis()-release)).coerceAtLeast(0))
            example=example.pause(settings.letterPauseMs,settings)
            delay((settings.wordPauseMs-(SystemClock.uptimeMillis()-release)).coerceAtLeast(0))
            example=example.pause(settings.wordPauseMs,settings)
        }
    }
    Column(Modifier.fillMaxWidth().padding(12.dp).testTag("morse-settings-preview"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Essaie ici",fontWeight=FontWeight.Bold,fontSize=13.sp,modifier=Modifier.weight(1f))
            IconButton({ example=MorsePreviewState() },Modifier.size(32.dp),enabled=example.code.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep,"Effacer l’essai Morse",Modifier.size(20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min=28.dp)) {
            if(example.code.isEmpty())Text("Aucun effet sur ta progression.",color=Muted,fontSize=12.sp)
            else MorseVisual(example.code,compact=true)
        }
        if(example.code.isNotEmpty())Text(MorseReference.decode(example.code).output.trim(),
            Modifier.testTag("morse-preview-text"),color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
        if(settings.singleKey)Text("Pauses : lettre ${settings.letterPauseMs} ms · mot ${settings.wordPauseMs} ms",
            color=Muted,fontSize=11.sp,lineHeight=15.sp)
        MorseSignalInput(onTimedSignal={ symbol,down,up -> example=example.record(symbol,down,up,settings) },
            onPressChanged={ holding=it }) { symbol -> example=MorsePreviewState((example.code+symbol).takeLast(128)) }
    }
}
