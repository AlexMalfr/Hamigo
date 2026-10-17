package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
        Text("Essaie ici",fontWeight=FontWeight.Bold,fontSize=13.sp)
        if(code.isNotBlank()) {
            MorseVisual(code,compact=true)
            Text(MorseReference.decode(code).output.trim(),Modifier.testTag("morse-preview-text"),color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
        }
        MorseComposerInput(code,settingsOverride=settings){code=it}
        TextButton(feedbackClick {code=""},enabled=code.isNotEmpty(),modifier=Modifier.semantics {contentDescription="Effacer l’essai Morse"}){Text("Effacer l’essai")}
    }
}
