package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun FeedbackSettings(model:AppModel) {
    val feedback=model.interactionFeedback
    val settings=feedback.settings
    Panel(Modifier.testTag("feedback-settings")) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            listOf(true,false).forEach {sound->
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        Icon(if(sound)Icons.Rounded.VolumeUp else Icons.Rounded.Vibration,null,Modifier.size(19.dp),tint=Teal)
                        Text(if(sound)"Sons" else "Vibrations",fontSize=14.sp,fontWeight=FontWeight.Bold)
                    }
                    Switch(if(sound)settings.sound else settings.haptics,{value->
                        FeedbackPreferences.save(model.progress.prefs,if(sound)feedback.settings.copy(sound=value) else feedback.settings.copy(haptics=value))
                        if(value)feedback.event(if(sound)FeedbackCue.CLICK else FeedbackCue.SNAP)
                    },Modifier.testTag(if(sound)"feedback-sound" else "feedback-haptics").semantics {contentDescription=if(sound)"Sons" else "Vibrations"})
                }
            }
        }
    }
}

@Composable internal fun SoundFeedbackButton() {
    val feedback=LocalAppFeedback.current ?: return
    val context=androidx.compose.ui.platform.LocalContext.current
    val sound=feedback.settings.sound
    IconToggleButton(sound,{enabled->
        FeedbackPreferences.save(context.getSharedPreferences("hamigo",android.content.Context.MODE_PRIVATE),feedback.settings.copy(sound=enabled))
    },Modifier.testTag("question-sound-toggle").semantics {stateDescription=if(sound)"Sons activés" else "Sons désactivés"}) {
        Icon(if(sound)Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,if(sound)"Couper les sons" else "Activer les sons",tint=if(sound)Muted else Teal,modifier=Modifier.size(22.dp))
    }
}
