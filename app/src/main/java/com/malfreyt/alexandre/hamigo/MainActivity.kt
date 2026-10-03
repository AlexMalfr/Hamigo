package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var model: AppModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model=ViewModelProvider(this)[AppModel::class.java];model.initialize(this)
        receive(intent)
        setContent { HamigoTheme { HamigoApp(model) } }
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);receive(intent)}
    override fun onResume() {super.onResume();if(::model.isInitialized && model.content!=null) {model.refresh();model.refreshSocial()}}
    override fun onPause() {stopMorse();super.onPause()}
    private fun receive(intent: Intent?) {
        if(intent?.getStringExtra("hamigo_route")=="path") model.route="path"
        if(intent?.action !in listOf(Intent.ACTION_SEND,Intent.ACTION_VIEW)) return
        @Suppress("DEPRECATION")
        val uri=if(intent?.action==Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else intent?.data
        if(uri!=null && uri.scheme in listOf("https","hamigo")) {
            val id=FriendInvite.parse(uri.toString())
            if(id!=null) model.pendingInvite=id
            else if(uri.scheme=="https" && uri.host=="alexmalfr.github.io" && uri.path=="/hamigo/" && uri.query==null) model.route="friends"
            else model.message="Cette invitation Hamigo n’est pas valide."
            return
        }
        if(uri!=null) model.task { model.incoming=withContext(Dispatchers.IO) {
            readImport(contentResolver,uri)
        } }
    }
}

@Composable fun HamigoApp(model: AppModel) {
    val content=model.content
    val tick=model.revision
    val context=LocalContext.current
    var quit by remember {mutableStateOf(false)}
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(model.message) {model.message?.let {snackbar.showSnackbar(it);model.message=null}}
    if(content==null) {
        Box(Modifier.fillMaxSize().background(Cream),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Pico(Modifier.size(160.dp));Text("On accorde les antennes…",fontWeight=FontWeight.Bold,fontSize=22.sp)
                model.error?.let {Text(it,Modifier.padding(24.dp),color=Coral)} ?: CircularProgressIndicator(color=Teal)
            }
        };return
    }
    val p=model.progress
    BackHandler(enabled=model.session!=null || model.lesson!=null || model.resource!=null || model.route=="settings") {
        when { model.session!=null -> quit=true; model.lesson!=null ->model.lesson=null;model.resource!=null->model.resource=null;else->model.route="profile" }
    }
    Scaffold(containerColor=Cream,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(model.session==null && model.lesson==null && model.resource==null && model.route!="settings") {
            NavigationBar(containerColor=Color.White,tonalElevation=0.dp) {
                listOf(Triple("path","Parcours",Icons.Rounded.Route),Triple("practice","Défis",Icons.Rounded.Bolt),
                    Triple("resources","Mémo",Icons.Rounded.MenuBook),Triple("friends","Équipe",Icons.Rounded.Groups),
                    Triple("profile","Moi",Icons.Rounded.Person)).forEach { (id,label,icon)->
                    NavigationBarItem(selected=model.route==id,onClick={model.route=id},icon={Icon(icon,label)},label={Text(label,fontSize=11.sp)},
                        colors=NavigationBarItemDefaults.colors(indicatorColor=Mist,selectedIconColor=Teal,selectedTextColor=Teal))
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                model.session!=null -> QuizScreen(model)
                model.lesson!=null -> LessonScreen(model,model.lesson!!)
                model.resource!=null -> ReferenceDetailScreen(model,model.resource!!)
                model.route=="path" -> PathScreen(model,content)
                model.route=="practice" -> PracticeHubScreen(model,content)
                model.route=="resources" -> ResourceLibraryScreen(model,content)
                model.route=="friends" -> FriendsScreen(model)
                model.route=="settings" -> SettingsScreen(model)
                else -> ProfileScreen(model,content)
            }
        }
    }
    if(quit) AlertDialog(onDismissRequest={quit=false},title={Text("Une pause radio ?")},text={Text("Tes réponses et ton XP sont déjà sauvegardés. Tu pourras reprendre cette leçon depuis le parcours.")},
        confirmButton={TextButton({quit=false;model.leaveSession()}){Text("Quitter la séance")}},dismissButton={TextButton({quit=false}){Text("Continuer")}})
    if(model.showWelcome) {
        var name by remember {mutableStateOf("")}
        AlertDialog(onDismissRequest={},icon={Pico(Modifier.size(110.dp))},title={Text("Bienvenue sur les ondes !")},
            text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("Quelques minutes par jour pour préparer ton certificat radioamateur. Pico t'accompagne, une notion à la fois.")
                OutlinedTextField(name,{name=it.take(40)},label={Text("Ton pseudo")},singleLine=true)
                Text("Tout fonctionne hors ligne. Connecte GitHub pour retrouver ton voyage sur plusieurs appareils et jouer avec ton équipe.",fontSize=12.sp,color=Muted)
            }},confirmButton={Column(Modifier.fillMaxWidth()) {
                Action("Se connecter avec GitHub") {model.welcome(name);model.startGitHubConnection()}
                TextButton({model.welcome(name)},Modifier.align(Alignment.CenterHorizontally)) {Text("Commencer sur cet appareil")}
            }})
    }
    model.incoming?.let { json ->
        AlertDialog(onDismissRequest={model.incoming=null},title={Text("Restaurer la sauvegarde ?")},
            text={Text("La sauvegarde remplacera ta progression locale actuelle. Ton compte GitHub reste connecté.")},
            confirmButton={TextButton({model.restore(json);model.incoming=null}){Text("Restaurer")}},
            dismissButton={TextButton({model.incoming=null}){Text("Annuler")}})
    }
    if(!model.showWelcome && model.oauthSession==null) model.pendingInvite?.let {
        AlertDialog(onDismissRequest={model.pendingInvite=null},icon={Pico(Modifier.size(80.dp),mood=MascotMood.GOOFY,pose=MascotPose.WAVE)},
            title={Text("Rejoindre cette équipe ?")},text={Text("Hamigo va récupérer le résumé de progression de cet équipier et l’ajouter à ton équipe.")},
            confirmButton={TextButton({model.acceptInvite()},enabled=!model.busy) {Text("Ajouter l’équipier")}},dismissButton={TextButton({model.pendingInvite=null}) {Text("Annuler")}})
    }
    model.oauthSession?.let {device ->
        val clipboard=LocalClipboardManager.current
        AlertDialog(onDismissRequest={model.cancelTask()},title={Text("Connexion à GitHub")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Entre ce code dans la page GitHub qui s’ouvre :")
            Text(device.userCode,fontSize=30.sp,fontWeight=FontWeight.ExtraBold,color=Teal)
            TextButton({clipboard.setText(AnnotatedString(device.userCode));model.message="Code copié."}) {Text("Copier le code")}
            Text("Autorise Hamigo à utiliser les Gists de ton compte. L’app attend ta validation ici.",fontSize=13.sp)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }},confirmButton={TextButton({clipboard.setText(AnnotatedString(device.userCode));openLink(context,device.verificationUri)}) {Text("Ouvrir GitHub")}},dismissButton={TextButton({model.cancelTask()}) {Text("Annuler")}})
    }
}

@Composable fun PageHeader(title: String,subtitle: String="",back: (() ->Unit)?=null) {
    if(back!=null) IconButton(back,Modifier.offset(x=(-12).dp)){Icon(Icons.Rounded.ArrowBack,"Retour")}
    BigTitle(title,subtitle)
}

@Composable fun PathScreen(model:AppModel, content:Content) {
    val p=model.displayedProgress ?: model.progress
    val completed=p.completed
    var expanded by remember {mutableStateOf(content.chapters.indexOfFirst { c->c.lessons.any {it.id !in completed} }.coerceAtLeast(0))}
    val next=content.nextLesson(completed)
    val due=p.due(content)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column {Eyebrow("HAMIGO");Text("Salut ${p.name.split(' ').first()} !",fontSize=23.sp,fontWeight=FontWeight.ExtraBold)}
                Column(horizontalAlignment=Alignment.End) {Text("🔥 ${p.streak} jours",fontWeight=FontWeight.Bold,color=Color(0xFFB44D30));Text("⚡ ${p.xp} XP",fontSize=13.sp,color=Teal)}
            }
        }
        item {
            Panel(color=Mist) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Eyebrow("TA MISSION DU JOUR");Spacer(Modifier.height(5.dp));Text("Une petite onde,\nun grand déclic.",fontSize=23.sp,lineHeight=27.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(5.dp));Text("${p.todayXp} / ${p.dailyGoal} XP aujourd'hui",fontSize=13.sp,color=Muted)}
                    Pico(Modifier.size(94.dp),mood=if(p.todayXp>=p.dailyGoal)MascotMood.CELEBRATE else if(p.streak>0)MascotMood.HAPPY else MascotMood.DETERMINED,pose=if(p.todayXp>=p.dailyGoal)MascotPose.DANCE else MascotPose.WAVE)
                }
                LinearProgressIndicator(progress={ (p.todayXp.toFloat()/p.dailyGoal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(8.dp),color=Teal,trackColor=Color.White)
                Action(if(next==null)"Parcours terminé · refaire un défi" else "Continuer · ${next.title}") {
                    if(next!=null)model.startLesson(next) else model.route="practice"
                }
            }
        }
        if(due.isNotEmpty()) item {
            Surface(onClick={if(due.isNotEmpty())model.startQuestions("Les ondes reviennent",due.take(12)) else model.route="resources"},shape=RoundedCornerShape(20.dp),color=Color(0xFFFFEDDC)) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh,"Réviser",tint=Color(0xFFB76D36));Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {Text(if(due.isEmpty())"Ta mémoire prend de l'avance" else "${due.size} notions à revoir",fontWeight=FontWeight.Bold);Text(if(due.isEmpty())"Découvre les fiches mémo" else "Une révision au bon moment, ça reste.",fontSize=12.sp,color=Muted)}
                    Icon(Icons.Rounded.ChevronRight,null)
                }
            }
        }
        item {BigTitle("Ton voyage radio","${completed.size} / ${content.lessons.size} leçons · à ton rythme")}
        items(content.chapters.size) { index ->
            val c=content.chapters[index];val color=ChapterColors[index%ChapterColors.size]
            val finished=c.lessons.count {it.id in completed}
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(onClick={expanded=if(expanded==index)-1 else index},shape=RoundedCornerShape(22.dp),color=color) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("%02d".format(index+1),Modifier.background(Color.White.copy(alpha=.18f),RoundedCornerShape(14.dp)).padding(12.dp),fontSize=21.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
                        Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)) {Text(c.title,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("${finished}/${c.lessons.size} · ${c.subtitle}",color=Color.White.copy(alpha=.85f),fontSize=12.sp)}
                        Icon(if(expanded==index)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null,tint=Color.White)
                    }
                }
                if(expanded==index) c.lessons.forEachIndexed { i,l ->
                    val done=l.id in completed;val active=l.id==next?.id
                    Row(Modifier.fillMaxWidth().padding(start=if(i%2==0)12.dp else 34.dp,end=if(i%2==0)34.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(onClick={model.startLesson(l)},modifier=Modifier.size(54.dp),shape=CircleShape,color=if(done)color else if(active)Gold else Color.White,shadowElevation=if(active)5.dp else 1.dp) {
                            Box(contentAlignment=Alignment.Center) {Icon(if(done)Icons.Rounded.Check else if(active)Icons.Rounded.PlayArrow else Icons.Rounded.RadioButtonUnchecked,l.title,tint=if(done)Color.White else color)}
                        }
                        Spacer(Modifier.width(15.dp))
                        Column(Modifier.weight(1f).clickable {model.startLesson(l)}.padding(vertical=13.dp)) {
                            Text(l.title,fontWeight=if(active)FontWeight.ExtraBold else FontWeight.Bold,fontSize=15.sp)
                            Text(if(done)"Signal reçu ✓" else if(active)"À toi de jouer · 5 à 8 min" else l.summary,maxLines=2,fontSize=12.sp,color=Muted)
                        }
                    }
                }
            }
        }
        item {Text("Parcours libre : tu peux explorer une leçon à tout moment. La prochaine étape conseillée reste la même pour tous.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(vertical=8.dp))}
    }
}

@Composable fun LessonScreen(model:AppModel,lesson:Lesson) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            PageHeader(lesson.title,lesson.summary){model.lesson=null}
            Row(verticalAlignment=Alignment.CenterVertically) {Pico(Modifier.size(64.dp),mood=MascotMood.THINKING,pose=MascotPose.POINT);Spacer(Modifier.width(12.dp));Text("D'abord le déclic.\nEnsuite, à toi de jouer.",fontWeight=FontWeight.Bold,color=Teal)}
            lesson.body.forEach { Text(it,fontSize=16.sp,lineHeight=24.sp) }
            if(lesson.formula.isNotBlank()) Panel(color=Mist){Eyebrow("À RETENIR");Text(lesson.formula,fontSize=21.sp,fontWeight=FontWeight.Bold)}
            Text("Cours original Hamigo, adapté des ressources F6KGL. Les références sont dans les réglages.",fontSize=12.sp,color=Muted)
        }
        Action("À toi de jouer · ${lesson.questions.size} à ${lesson.questions.size+2} défis",Modifier.padding(16.dp)) {model.startQuestions(lesson.title,LessonSessionBuilder.create(lesson),lesson.id)}
    }
}

@Composable fun ProfileScreen(model:AppModel,content:Content) {
    val p=model.displayedProgress ?: model.progress
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){BigTitle(p.name,"Un peu chaque jour, beaucoup à l'arrivée.")};IconButton({model.route="settings"}){Icon(Icons.Rounded.Settings,"Réglages")}}}
        item {Panel(color=Mist){Row(verticalAlignment=Alignment.CenterVertically){Pico(Modifier.size(80.dp),mood=if(p.streak>0)MascotMood.CELEBRATE else MascotMood.HAPPY,pose=if(p.streak>0)MascotPose.JUMP else MascotPose.WAVE);Column(Modifier.weight(1f)){Text("Niveau ${1+p.xp/250}",fontSize=24.sp,fontWeight=FontWeight.ExtraBold);Text("${p.xp} XP · 🔥 ${p.streak} jours",color=Teal,fontWeight=FontWeight.Bold)}};LinearProgressIndicator(progress={(p.xp%250)/250f},modifier=Modifier.fillMaxWidth(),color=Teal,trackColor=Color.White);Text("${250-p.xp%250} XP avant le prochain niveau",fontSize=12.sp,color=Muted)}}
        item {
            Panel {
                Text("Cette semaine",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth().height(100.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
                    val days=(6L downTo 0L).map{LocalDate.now().minusDays(it)}
                    val maximum=days.maxOf{p.dayXp(it)}.coerceAtLeast(p.dailyGoal)
                    days.forEach {day->Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text("${p.dayXp(day)}",fontSize=10.sp,color=Muted)
                        Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(p.dayXp(day).toFloat()/maximum).background(if(day==LocalDate.now())Coral else Teal,RoundedCornerShape(5.dp)))
                        }
                        Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,java.util.Locale.FRENCH),fontSize=11.sp)
                    }}
                }
                Text("${p.weeklyXp} XP en 7 jours · objectif ${p.dailyGoal} XP/jour",fontSize=12.sp,color=Muted)
            }
        }
        item {Panel {Text("Ton signal se renforce",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("${p.completed.size} / ${content.lessons.size} leçons terminées\n${p.reviews.values.count{it.repetitions>=3}} notions consolidées\n${p.totalAnswers} réponses · ${if(p.totalAnswers==0)0 else p.totalCorrect*100/p.totalAnswers}% de réussite",fontSize=14.sp,lineHeight=22.sp);Action("Partager ma progression"){NativeShare.progressImage(context,p.snapshot())}}}
        item {Panel {Text("La mémoire aime les retrouvailles",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("Les bonnes réponses reviennent après 1 jour, puis 6 jours, puis plus loin selon ta facilité. Les erreurs reviennent après 10 minutes. Les flashcards te laissent choisir leur difficulté.",fontSize=14.sp,color=Muted,lineHeight=22.sp)}}
    }
}
