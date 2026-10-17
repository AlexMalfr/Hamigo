package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class MorseProfile(val title:String,val description:String,val settings:MorseInputSettings)
internal val MorseProfiles=listOf(
    MorseProfile("Pas à pas","Deux touches distinctes. Tu choisis quand passer à la lettre suivante.",MorseInputSettings(thresholdMs=450)),
    MorseProfile("En rythme","Deux touches. Les silences séparent automatiquement tes lettres et tes mots.",MorseInputSettings(thresholdMs=400,autoSpaces=true)),
    MorseProfile("Au manipulateur","Une seule touche : joue sur la durée des appuis et des silences.",MorseInputSettings(singleKey=true,thresholdMs=250,autoSpaces=true)))

@Composable internal fun MorseSettingsControls(settings:MorseInputSettings,save:(MorseInputSettings)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Mode de saisie",fontWeight=FontWeight.Bold,fontSize=14.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!settings.singleKey,feedbackClick {save(settings.copy(singleKey=false))},label={Text("Deux boutons")},modifier=Modifier.weight(1f))
            FilterChip(settings.singleKey,feedbackClick {save(settings.copy(singleKey=true))},label={Text("Un bouton")},modifier=Modifier.weight(1f))
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("Espaces automatiques",fontWeight=FontWeight.Bold,fontSize=14.sp);Text("Les silences séparent les lettres et les mots.",fontSize=12.sp,color=Muted)}
            Switch(settings.autoSpaces,{save(settings.copy(autoSpaces=it))},Modifier.testTag("morse-auto-spaces"))
        }
        if(settings.singleKey||settings.autoSpaces) {
            var threshold by remember(settings.thresholdMs){mutableFloatStateOf(settings.thresholdMs.toFloat())}
            Row(verticalAlignment=Alignment.CenterVertically) {Text("Rythme de saisie",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=14.sp);Text("${threshold.toInt()} ms",color=Teal,fontSize=12.sp)}
            Slider(threshold,{threshold=it},valueRange=150f..600f,steps=8,onValueChangeFinished={save(settings.copy(thresholdMs=(kotlin.math.round(threshold/50)*50).toInt()))},modifier=Modifier.testTag("morse-timing"))
        }
    }
}
