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
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

internal fun openSystemSpeechSettings(context:Context) {
    for(action in listOf("com.android.settings.TTS_SETTINGS",Settings.ACTION_ACCESSIBILITY_SETTINGS,Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch(_:android.content.ActivityNotFoundException) {
            // Some manufacturers do not expose the dedicated TTS page.
        } catch(_:SecurityException) {
            // Try the public settings page if a manufacturer restricts this activity.
        }
    }
    Toast.makeText(context,"Impossible d’ouvrir les paramètres système.",Toast.LENGTH_SHORT).show()
}

@Composable internal fun FeedbackSettings(model:AppModel) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val speechContext=LocalSpeechInputHost.current?.context ?: context
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
                        model.refreshSocial()
                        if(value)feedback.event(if(sound)FeedbackCue.CLICK else FeedbackCue.SNAP)
                    },Modifier.testTag(if(sound)"feedback-sound" else "feedback-haptics").semantics {contentDescription=if(sound)"Sons" else "Vibrations"})
                }
            }
        }
        TextButton(feedbackClick {openSystemSpeechSettings(speechContext)},
            Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("system-tts-settings"),
            contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
            Icon(Icons.Rounded.RecordVoiceOver,null,Modifier.size(20.dp),tint=Teal)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Voix de synthèse",color=Teal,fontSize=14.sp)
                Text("Hamigo utilise le TTS système.",color=Muted,fontSize=12.sp,lineHeight=16.sp)
            }
            Icon(Icons.Rounded.Settings,null,Modifier.size(18.dp),tint=Teal)
        }
    }
}

@Composable internal fun SoundFeedbackButton() {
    val feedback=LocalAppFeedback.current ?: return
    val context=androidx.compose.ui.platform.LocalContext.current
    val sound=feedback.settings.sound
    IconToggleButton(sound,{enabled->
        FeedbackPreferences.save(context.getSharedPreferences("hamigo",android.content.Context.MODE_PRIVATE),feedback.settings.copy(sound=enabled))
        if(context !is DiagnosticContext)com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler.enqueue(context)
    },Modifier.testTag("question-sound-toggle").semantics {stateDescription=if(sound)"Sons activés" else "Sons désactivés"}) {
        Icon(if(sound)Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,if(sound)"Couper les sons" else "Activer les sons",tint=if(sound)Muted else Teal,modifier=Modifier.size(22.dp))
    }
}
