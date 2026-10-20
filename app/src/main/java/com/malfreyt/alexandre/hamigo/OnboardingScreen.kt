package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.malfreyt.alexandre.hamigo.platform.DailyReminder

private val onboardingSteps=listOf("Pseudo","Objectif","Rappel","Morse","Micro","GitHub")
internal fun onboardingMorseSettings(prefs:android.content.SharedPreferences):MorseInputSettings {
    val existing=GameplayPreferences.read(prefs)
    // The historic 300 ms default matched none of the three profiles, including in previews.
    return if(existing==MorseInputSettings(liveSound=existing.liveSound))MorseProfiles.first().settings.copy(liveSound=existing.liveSound)
    else existing
}
private val onboardingMorseSaver=listSaver<MorseInputSettings,Any>(
    save={listOf(it.singleKey,it.thresholdMs,it.liveSound,it.autoSpaces)},
    restore={MorseInputSettings(it[0] as Boolean,it[1] as Int,it[2] as Boolean,it[3] as Boolean)})

/** Full-screen first run; confirmed choices survive an interrupted setup, without resetting users. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun OnboardingScreen(model:AppModel,simulation:Boolean=false,onSimulationFinished:()->Unit={}) {
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val prefs=model.progress.prefs
    val speechMode=remember(model.revision){SpeechInputPreferences.read(prefs)}
    var step by rememberSaveable {mutableIntStateOf(prefs.getInt("onboardingStep",0).coerceIn(0,5))}
    var name by rememberSaveable {mutableStateOf(prefs.getString("onboardingName",prefs.getString("name","")) ?: "")}
    var goal by rememberSaveable {mutableIntStateOf(model.progress.dailyGoal.takeIf {it in DailyGoals.values} ?: DailyGoals.values[1])}
    var hour by rememberSaveable {mutableIntStateOf(prefs.getInt("onboardingReminderHour",prefs.getInt("reminderHour",20)).coerceIn(0,23))}
    var minute by rememberSaveable {mutableIntStateOf(prefs.getInt("onboardingReminderMinute",prefs.getInt("reminderMinute",0)).coerceIn(0,59))}
    var morse by rememberSaveable(stateSaver=onboardingMorseSaver) {mutableStateOf(onboardingMorseSettings(prefs))}
    var advancedMorse by rememberSaveable {mutableStateOf(false)}
    var showMorsePreview by rememberSaveable {mutableStateOf(false)}
    var permissionPending by remember {mutableStateOf(false)}
    var permissionError by rememberSaveable {mutableStateOf(false)}
    fun moveTo(next:Int) {
        focus.clearFocus()
        step=next
        prefs.edit().putInt("onboardingStep",next).apply()
    }
    fun confirmReminder(enabled:Boolean) {
        if(!simulation)DailyReminder.configure(context,enabled,hour,minute)
        model.refresh()
        moveTo(3)
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        permissionPending=false
        if(granted)confirmReminder(true)
        else {DailyReminder.configure(context,false,hour,minute);permissionError=true}
    }
    fun finish(connect:Boolean) {
        if(simulation){onSimulationFinished();return}
        model.welcome(name.trim())
        if(connect) {model.route="friends";model.startGitHubConnection()}
        else model.route="path"
    }
    fun continueStep() {
        when(step) {
            0 -> if(name.isNotBlank()) {model.progress.name=name.trim();moveTo(1)}
            1 -> {model.progress.setDailyGoalChoice(DailyGoals.values.indexOf(goal));model.refresh();moveTo(2)}
            2 -> {
                permissionError=false
                if(simulation)confirmReminder(true)
                else if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                    permissionPending=true
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if(NotificationManagerCompat.from(context).areNotificationsEnabled())confirmReminder(true)
                else permissionError=true
            }
            3 -> {GameplayPreferences.save(prefs,morse);moveTo(4)}
            4 -> moveTo(5)
            5 -> finish(!model.sync.tokens.hasToken())
        }
    }
    BackHandler(enabled=step>0 && !permissionPending) {moveTo(step-1)}
    Column(Modifier.fillMaxSize().background(Cream).windowInsetsPadding(WindowInsets.systemBars).imePadding()
        .testTag("onboarding")) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                onboardingSteps.forEachIndexed {index,label ->
                    val fraction by animateFloatAsState(if(index<step)1f else if(index==step).16f else 0f,tween(280),label="onboarding-bar")
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Ink.copy(alpha=.1f))
                        .testTag("onboarding-bar-$index").semantics {
                            contentDescription="$label : ${if(index<step)"terminé" else if(index==step)"en cours" else "à venir"}"
                        }) {Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Teal))}
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(step>0)IconButton(feedbackClick {moveTo(step-1)},Modifier.size(40.dp),enabled=!permissionPending) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Étape précédente")
                } else if(simulation)IconButton(onSimulationFinished){Icon(Icons.Rounded.Close,"Fermer la simulation")}
                else Spacer(Modifier.width(8.dp))
                Eyebrow(if(simulation)"SIMULATION" else "HAMIGO")
                Spacer(Modifier.weight(1f))
                Text("${step+1}/${onboardingSteps.size} · ${onboardingSteps[step]}",fontSize=12.sp,lineHeight=16.sp,color=Muted,modifier=Modifier.testTag("onboarding-stage"))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
            val pageHeight=maxHeight
            val heroSize=if(WindowInsets.isImeVisible)112.dp else (maxHeight*(.35f/LocalDensity.current.fontScale.coerceAtLeast(1f))).coerceIn(140.dp,240.dp)
            AnimatedContent(targetState=step,modifier=Modifier.fillMaxSize(),label="onboarding-step",
                transitionSpec={
                    val direction=if(targetState>initialState)1 else -1
                    (fadeIn(tween(220))+slideInHorizontally(tween(260)){it/5*direction}) togetherWith
                        (fadeOut(tween(160))+slideOutHorizontally(tween(260)){-it/5*direction})
                }) {page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .then(if(page==3)Modifier.heightIn(min=pageHeight) else Modifier).padding(horizontal=24.dp,vertical=8.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(if(page==3)10.dp else 16.dp)) {
                        if(page!=4)OnboardingPico(page,if(page==3)heroSize.coerceAtMost(96.dp) else heroSize)
                        val title=when(page) {0->"Bienvenue dans Hamigo";1->"Un objectif à ton rythme";2->"On garde le rendez-vous ?";3->"Comment veux-tu saisir le Morse ?";4->"Essaie la réponse vocale";else->"Retrouve ta progression"}
                        val description=when(page) {
                            0->"Moi, c’est Pico ! On prépare ton certificat radioamateur, une notion à la fois. Comment dois-je t’appeler ?"
                            1->"Choisis les XP que tu veux gagner chaque jour. Tu pourras ajuster ton objectif à tout moment."
                            2->"Un petit rappel de Pico pour penser à ta séance, à l’heure qui te convient."
                            3->"Tu pourras le changer dans les paramètres."
                            4->"Réponds à voix haute, ou au clavier."
                            else->"Avec GitHub, sauvegarde tes révisions et rejoins tes amis pour vous motiver ensemble."
                        }
                        Text(title,fontSize=28.sp,lineHeight=32.sp,fontWeight=FontWeight.ExtraBold,color=Ink,textAlign=TextAlign.Center)
                        Text(description,color=Muted,fontSize=15.sp,lineHeight=22.sp,textAlign=TextAlign.Center)
                        when(page) {
                            0->OutlinedTextField(name,{name=it.take(40);prefs.edit().putString("onboardingName",name).apply()},
                                label={Text("Ton pseudo")},placeholder={Text("Par exemple, Alex")},singleLine=true,
                                modifier=Modifier.fillMaxWidth().widthIn(max=480.dp).testTag("onboarding-name"),
                                keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Words,imeAction=ImeAction.Next),
                                keyboardActions=KeyboardActions(onNext={continueStep()}))
                            1->Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                DailyGoals.values.zip(listOf("Léger","Régulier","Soutenu","Intensif")).chunked(2).forEach {row ->
                                    Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                        row.forEach {(value,label)->
                                            Surface(onClick=feedbackClick {goal=value},modifier=Modifier.weight(1f).fillMaxHeight().testTag("onboarding-goal-$value").semantics {selected=goal==value},
                                                shape=RoundedCornerShape(20.dp),color=if(goal==value)Mist else Color.White,
                                                border=androidx.compose.foundation.BorderStroke(if(goal==value)2.dp else 1.dp,if(goal==value)Teal else Ink.copy(alpha=.1f))) {
                                                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                                        Text("$value XP",Modifier.weight(1f),fontSize=21.sp,lineHeight=25.sp,fontWeight=FontWeight.ExtraBold,color=Teal)
                                                        if(goal==value)Icon(Icons.Rounded.CheckCircle,"Objectif sélectionné",Modifier.size(20.dp),tint=Teal)
                                                    }
                                                    Text(label,fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,color=Ink)
                                                    Text(if(value==60)"Conseillé pour démarrer" else "Par jour",fontSize=11.sp,lineHeight=15.sp,color=Muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2-> {
                                OutlinedButton(feedbackClick {TimePickerDialog(context,{_,h,m->
                                    hour=h;minute=m
                                    prefs.edit().putInt("onboardingReminderHour",h).putInt("onboardingReminderMinute",m).apply()
                                },hour,minute,true).show()},Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("onboarding-reminder-time")) {
                                    Icon(Icons.Rounded.Schedule,null);Spacer(Modifier.width(12.dp))
                                    Text("%02d:%02d".format(hour,minute),fontSize=26.sp,fontWeight=FontWeight.ExtraBold)
                                    Spacer(Modifier.weight(1f));Icon(Icons.Rounded.Edit,"Choisir l’heure du rappel")
                                }
                                if(permissionError) {
                                    Text("Les notifications restent désactivées. Tu peux continuer sans rappel.",color=MaterialTheme.colorScheme.error,fontSize=13.sp,lineHeight=18.sp,textAlign=TextAlign.Center)
                                    TextButton(feedbackClick {context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) {
                                        Text("Ouvrir les réglages de notifications")
                                    }
                                } else Text("Un rappel par jour. Tu gardes la main dans les paramètres.",fontSize=12.sp,lineHeight=16.sp,color=Muted,textAlign=TextAlign.Center)
                            }
                            3-> {
                                if(!advancedMorse)MorseProfiles.forEachIndexed {index,profile ->
                                    val chosen=morse.singleKey==profile.settings.singleKey&&morse.autoSpaces==profile.settings.autoSpaces&&morse.thresholdMs==profile.settings.thresholdMs
                                    Surface(onClick=feedbackClick {morse=profile.settings.copy(liveSound=morse.liveSound)},modifier=Modifier.testTag("onboarding-morse-profile-$index").semantics {selected=chosen},color=if(chosen)Mist else Color.White,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(if(chosen)2.dp else 1.dp,if(chosen)Teal else Mist)) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                Text(profile.title,Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Teal)
                                                Row(Modifier.semantics {contentDescription="Difficulté ${index+1} sur 3"},horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                                                    repeat(index+1){Icon(Icons.Rounded.Bolt,null,Modifier.size(17.dp),tint=if(index==2)Coral else Teal)}
                                                }
                                            }
                                            Text(when(index){0->"Recommandé pour les débutants";1->"Pour prendre le rythme";else->"Plus réaliste · avancé"},fontSize=11.sp,lineHeight=15.sp,fontWeight=FontWeight.Bold,color=if(index==2)Coral else Teal)
                                            Text(profile.description,fontSize=12.sp,lineHeight=17.sp,color=Muted)
                                        }
                                    }
                                }
                                if(advancedMorse)MorseSettingsControls(morse){morse=it}

                            }
                            4->OnboardingMicrophoneTest(page==step,speechMode) {mode->
                                SpeechInputPreferences.save(prefs,mode);model.refresh();model.refreshSocial()
                            }
                            5-> {
                                Surface(color=Mist,shape=RoundedCornerShape(20.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                        OnboardingBenefit(Icons.Rounded.CloudDone,"Ta progression sauvegardée")
                                        OnboardingBenefit(Icons.Rounded.Groups,"Ton équipe et ses progrès")
                                        OnboardingBenefit(Icons.Rounded.WifiOff,"Tes révisions accessibles hors ligne")
                                    }
                                }
                                Text("GitHub est facultatif. Tu peux le connecter plus tard.",fontSize=12.sp,lineHeight=16.sp,color=Muted,textAlign=TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if(page==3)Column(Modifier.fillMaxWidth().padding(top=10.dp),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            TextButton(feedbackClick {advancedMorse=!advancedMorse},Modifier.testTag("onboarding-morse-mode")) {
                                Text(if(advancedMorse)"Revenir aux profils" else "Réglages avancés",fontSize=12.sp)
                            }
                            if(showMorsePreview) {
                                val previewRequester=remember {BringIntoViewRequester()}
                                Surface(color=Mist,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().bringIntoViewRequester(previewRequester)) {MorseSettingsPreview(morse)}
                                LaunchedEffect(advancedMorse) {
                                    withFrameNanos { }
                                    previewRequester.bringIntoView()
                                }
                            } else OutlinedButton(feedbackClick {showMorsePreview=true},Modifier.testTag("onboarding-morse-try")) {
                                Icon(Icons.Rounded.PlayArrow,null,Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Essayer")
                            }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Button(feedbackClick {continueStep()},Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("onboarding-continue"),
                enabled=(step!=0 || name.isNotBlank()) && !permissionPending,shape=RoundedCornerShape(16.dp)) {
                Text(when(step){2->if(permissionPending)"Autorisation…" else "Activer le rappel";5->if(model.sync.tokens.hasToken())"C’est parti !" else "Se connecter avec GitHub";else->"Continuer"},fontWeight=FontWeight.Bold,fontSize=16.sp)
                Spacer(Modifier.width(8.dp));Icon(if(step==2)Icons.Rounded.NotificationsActive else Icons.AutoMirrored.Rounded.ArrowForward,null,Modifier.size(20.dp))
            }
            if(step==2||step==4||step==5)TextButton(feedbackClick {when(step){2->confirmReminder(false);4->moveTo(5);else->finish(false)}},Modifier.fillMaxWidth().testTag("onboarding-skip"),enabled=!permissionPending) {
                Text(when(step){2->"Pas maintenant";4->"Continuer sans microphone";else->"Commencer sans compte"})
            }
        }
    }
}

@Composable private fun OnboardingPico(step:Int,size:androidx.compose.ui.unit.Dp) {
    val mood=listOf(MascotMood.HAPPY,MascotMood.DETERMINED,MascotMood.GOOFY,MascotMood.THINKING,MascotMood.LISTENING,MascotMood.CELEBRATE)[step]
    val pose=listOf(MascotPose.WAVE,MascotPose.POINT,MascotPose.HUG,MascotPose.POINT,MascotPose.LISTEN,MascotPose.DANCE)[step]
    Box(Modifier.size(size).background(if(step==2)Color(0xFFFFE8E0) else Mist,CircleShape).testTag("onboarding-pico"),contentAlignment=Alignment.Center) {
        Pico(Modifier.fillMaxSize().padding(8.dp),mood=mood,pose=pose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OnboardingMicrophoneTest(visible:Boolean,mode:SpeechInputMode,onModeChanged:(SpeechInputMode)->Unit) {
    var transcript by rememberSaveable {mutableStateOf("")}
    val question=remember {Question("onboarding-microphone","Essaie quelques mots de l’alphabet international.",emptyList(),0,"",kind="spoken",skillIds=listOf("nato-recall"))}
    val state=rememberWrittenAnswerState("onboarding-microphone",question,{transcript=it},visible,
        permissionOccasion=MicrophonePermissionOccasion.ONBOARDING,requestPermissionOnOpen=true)
    Column(Modifier.fillMaxWidth().testTag("onboarding-microphone-test"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        TalkingPico(Modifier.size(80.dp),talking=false,listening=state.recognition.listening,
            mood=if(state.recognition.listening)MascotMood.LISTENING else MascotMood.HAPPY,pose=if(state.recognition.listening)MascotPose.LISTEN else MascotPose.WAVE)
        Text("Essaie « Alfa Bravo ».",fontSize=13.sp,lineHeight=18.sp,color=Muted,textAlign=TextAlign.Center)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("onboarding-speech-mode")) {
            listOf(SpeechInputMode.PUSH_TO_TALK to "Maintenir",SpeechInputMode.TOGGLE to "Bascule").forEachIndexed {index,(value,label)->
                SegmentedButton(selected=mode==value,onClick=feedbackClick {onModeChanged(value)},
                    shape=SegmentedButtonDefaults.itemShape(index,2),modifier=Modifier.testTag("onboarding-speech-mode-"+value.name)) {
                    Text(label)
                }
            }
        }
        if(!state.permission.granted)OutlinedButton(state.permission.request,
            Modifier.fillMaxWidth().testTag("onboarding-microphone-authorize"),enabled=!state.permission.pending) {
            Icon(Icons.Rounded.Mic,null);Spacer(Modifier.width(8.dp));Text(if(state.permission.requested)"Redemander l’autorisation" else "Autoriser le microphone")
        }
        WrittenAnswerInput(question,transcript,{transcript=it},visible,state)
        SpokenAnswerControls(state,visible)
    }
}

@Composable private fun OnboardingBenefit(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,tint=Teal,modifier=Modifier.size(22.dp))
        Text(label,fontSize=14.sp,lineHeight=20.sp,color=Ink,fontWeight=FontWeight.Medium)
    }
}
