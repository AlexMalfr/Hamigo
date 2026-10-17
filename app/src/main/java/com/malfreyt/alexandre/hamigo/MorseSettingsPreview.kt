package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun MorseSettingsPreview(settings:MorseInputSettings) {
    var code by remember {mutableStateOf("")}
    Column(Modifier.fillMaxWidth().padding(12.dp).testTag("morse-settings-preview").semantics {
        stateDescription=if(code.endsWith(" / "))"Pause entre mots" else if(code.endsWith(" "))"Pause entre lettres" else "Signal saisi"
    },verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val signalHeight=with(LocalDensity.current){16.sp.toDp()}.coerceAtLeast(21.dp)
        MorseComposerInput(code,settingsOverride=settings,preview={Box(Modifier.fillMaxWidth().heightIn(min=signalHeight)) {
            if(code.isNotBlank())MorseVisual(code,compact=true)
            else Text("Ton signal",color=Muted.copy(alpha=.65f),fontSize=12.sp,lineHeight=16.sp)
        }},belowPreview={Text(if(code.isBlank())"Ta traduction" else MorseReference.decode(code).output.trim().ifBlank {"…"},Modifier.testTag("morse-preview-text"),color=if(code.isBlank())Muted.copy(alpha=.65f) else Ink,fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold)}){code=it}
    }
}
