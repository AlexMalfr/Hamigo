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

private val onboardingSteps=listOf("Pseudo","Objectif","Rappel","GitHub")

/** Full-screen first run; confirmed choices survive an interrupted setup, without resetting users. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun OnboardingScreen(model:AppModel) {
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val prefs=model.progress.prefs
    var step by rememberSaveable {mutableIntStateOf(prefs.getInt("onboardingStep",0).coerceIn(0,3))}
    var name by rememberSaveable {mutableStateOf(prefs.getString("onboardingName",prefs.getString("name","")) ?: "")}
    var goal by rememberSaveable {mutableIntStateOf(model.progress.dailyGoal)}
    var hour by rememberSaveable {mutableIntStateOf(prefs.getInt("onboardingReminderHour",prefs.getInt("reminderHour",20)).coerceIn(0,23))}
    var minute by rememberSaveable {mutableIntStateOf(prefs.getInt("onboardingReminderMinute",prefs.getInt("reminderMinute",0)).coerceIn(0,59))}
    var permissionPending by remember {mutableStateOf(false)}
    var permissionError by rememberSaveable {mutableStateOf(false)}
    fun moveTo(next:Int) {
        focus.clearFocus()
        step=next
        prefs.edit().putInt("onboardingStep",next).apply()
    }
    fun confirmReminder(enabled:Boolean) {
        DailyReminder.configure(context,enabled,hour,minute)
        model.refresh()
        moveTo(3)
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        permissionPending=false
        if(granted)confirmReminder(true)
        else {DailyReminder.configure(context,false,hour,minute);permissionError=true}
    }
    fun finish(connect:Boolean) {
        model.welcome(name.trim())
        if(connect) {model.route="friends";model.startGitHubConnection()}
        else model.route="path"
    }
    fun continueStep() {
        when(step) {
            0 -> if(name.isNotBlank()) {model.progress.name=name.trim();moveTo(1)}
            1 -> {model.progress.setDailyGoal(goal);model.refresh();moveTo(2)}
            2 -> {
                permissionError=false
                if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                    permissionPending=true
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if(NotificationManagerCompat.from(context).areNotificationsEnabled())confirmReminder(true)
                else permissionError=true
            }
            3 -> finish(!model.sync.tokens.hasToken())
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
                if(step>0)IconButton({moveTo(step-1)},Modifier.size(40.dp),enabled=!permissionPending) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Étape précédente")
                } else Spacer(Modifier.width(8.dp))
                Eyebrow("HAMIGO")
                Spacer(Modifier.weight(1f))
                Text("${step+1}/4 · ${onboardingSteps[step]}",fontSize=12.sp,lineHeight=16.sp,color=Muted,modifier=Modifier.testTag("onboarding-stage"))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
            val heroSize=if(WindowInsets.isImeVisible)112.dp else (maxHeight*(.35f/LocalDensity.current.fontScale.coerceAtLeast(1f))).coerceIn(140.dp,240.dp)
            AnimatedContent(targetState=step,modifier=Modifier.fillMaxSize(),label="onboarding-step",
                transitionSpec={
                    val direction=if(targetState>initialState)1 else -1
                    (fadeIn(tween(220))+slideInHorizontally(tween(260)){it/5*direction}) togetherWith
                        (fadeOut(tween(160))+slideOutHorizontally(tween(260)){-it/5*direction})
                }) {page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=8.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    OnboardingPico(page,heroSize)
                    val title=when(page) {0->"Bienvenue dans Hamigo";1->"Un objectif à ton rythme";2->"On garde le rendez-vous ?";else->"Retrouve ta progression"}
                    val description=when(page) {
                        0->"Moi, c’est Pico ! On prépare ton certificat radioamateur, une notion à la fois. Comment dois-je t’appeler ?"
                        1->"Choisis les XP que tu veux gagner chaque jour. Tu pourras ajuster ton objectif à tout moment."
                        2->"Un petit rappel de Pico pour penser à ta séance, à l’heure qui te convient."
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
                            listOf(listOf(20 to "Léger",30 to "Régulier"),listOf(60 to "Soutenu",100 to "Intensif")).forEach {row ->
                                Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    row.forEach {(value,label)->
                                        Surface(onClick={goal=value},modifier=Modifier.weight(1f).fillMaxHeight().testTag("onboarding-goal-$value").semantics {selected=goal==value},
                                            shape=RoundedCornerShape(20.dp),color=if(goal==value)Mist else Color.White,
                                            border=androidx.compose.foundation.BorderStroke(if(goal==value)2.dp else 1.dp,if(goal==value)Teal else Ink.copy(alpha=.1f))) {
                                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                                Row(verticalAlignment=Alignment.CenterVertically) {
                                                    Text("$value XP",Modifier.weight(1f),fontSize=21.sp,lineHeight=25.sp,fontWeight=FontWeight.ExtraBold,color=Teal)
                                                    if(goal==value)Icon(Icons.Rounded.CheckCircle,"Objectif sélectionné",Modifier.size(20.dp),tint=Teal)
                                                }
                                                Text(label,fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,color=Ink)
                                                Text(if(value==30)"Conseillé pour démarrer" else "Par jour",fontSize=11.sp,lineHeight=15.sp,color=Muted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2-> {
                            OutlinedButton({TimePickerDialog(context,{_,h,m->
                                hour=h;minute=m
                                prefs.edit().putInt("onboardingReminderHour",h).putInt("onboardingReminderMinute",m).apply()
                            },hour,minute,true).show()},Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("onboarding-reminder-time")) {
                                Icon(Icons.Rounded.Schedule,null);Spacer(Modifier.width(12.dp))
                                Text("%02d:%02d".format(hour,minute),fontSize=26.sp,fontWeight=FontWeight.ExtraBold)
                                Spacer(Modifier.weight(1f));Icon(Icons.Rounded.Edit,"Choisir l’heure du rappel")
                            }
                            if(permissionError) {
                                Text("Les notifications restent désactivées. Tu peux continuer sans rappel.",color=MaterialTheme.colorScheme.error,fontSize=13.sp,lineHeight=18.sp,textAlign=TextAlign.Center)
                                TextButton({context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) {
                                    Text("Ouvrir les réglages de notifications")
                                }
                            } else Text("Un rappel par jour. Tu gardes la main dans les paramètres.",fontSize=12.sp,lineHeight=16.sp,color=Muted,textAlign=TextAlign.Center)
                        }
                        3-> {
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
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Button({continueStep()},Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("onboarding-continue"),
                enabled=(step!=0 || name.isNotBlank()) && !permissionPending,shape=RoundedCornerShape(16.dp)) {
                Text(when(step){2->if(permissionPending)"Autorisation…" else "Activer le rappel";3->if(model.sync.tokens.hasToken())"C’est parti !" else "Se connecter avec GitHub";else->"Continuer"},fontWeight=FontWeight.Bold,fontSize=16.sp)
                Spacer(Modifier.width(8.dp));Icon(if(step==2)Icons.Rounded.NotificationsActive else Icons.AutoMirrored.Rounded.ArrowForward,null,Modifier.size(20.dp))
            }
            if(step>=2)TextButton({if(step==2)confirmReminder(false) else finish(false)},Modifier.fillMaxWidth().testTag("onboarding-skip"),enabled=!permissionPending) {
                Text(if(step==2)"Pas maintenant" else "Commencer sans compte")
            }
        }
    }
}

@Composable private fun OnboardingPico(step:Int,size:androidx.compose.ui.unit.Dp) {
    val mood=listOf(MascotMood.HAPPY,MascotMood.DETERMINED,MascotMood.GOOFY,MascotMood.CELEBRATE)[step]
    val pose=listOf(MascotPose.WAVE,MascotPose.POINT,MascotPose.HUG,MascotPose.DANCE)[step]
    Box(Modifier.size(size).background(if(step==2)Color(0xFFFFE8E0) else Mist,CircleShape).testTag("onboarding-pico"),contentAlignment=Alignment.Center) {
        Pico(Modifier.fillMaxSize().padding(8.dp),mood=mood,pose=pose)
    }
}

@Composable private fun OnboardingBenefit(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,tint=Teal,modifier=Modifier.size(22.dp))
        Text(label,fontSize=14.sp,lineHeight=20.sp,color=Ink,fontWeight=FontWeight.Medium)
    }
}
