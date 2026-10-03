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
    private fun receive(intent: Intent?) {
        if(intent?.action !in listOf(Intent.ACTION_SEND,Intent.ACTION_VIEW)) return
        @Suppress("DEPRECATION")
        val uri=if(intent?.action==Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else intent?.data
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
                model.resource!=null -> ReferenceScreen(model,model.resource!!)
                model.route=="path" -> PathScreen(model,content)
                model.route=="practice" -> PracticeScreen(model,content)
                model.route=="resources" -> ResourcesScreen(model,content)
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
                Text("Tout l'apprentissage fonctionne hors ligne. Ton parcours reste sur cet appareil.",fontSize=12.sp,color=Muted)
            }},confirmButton={TextButton({model.welcome(name)}){Text("C'est parti !")}})
    }
    model.incoming?.let { json ->
        val backup=runCatching {JSONObject(json).optString("app")=="hamigo"}.getOrDefault(false)
        AlertDialog(onDismissRequest={model.incoming=null},title={Text(if(backup) "Restaurer la sauvegarde ?" else "Ajouter cette progression ?")},
            text={Text(if(backup) "La sauvegarde remplacera ta progression locale actuelle. Ton compte GitHub reste séparé." else "Ce fichier ajoute un ami à ton équipe. Sa progression ne remplace pas la tienne.")},
            confirmButton={TextButton({if(backup)model.restore(json) else model.importFriend(json);model.incoming=null}){Text(if(backup)"Restaurer" else "Ajouter")}},
            dismissButton={TextButton({model.incoming=null}){Text("Annuler")}})
    }
}

@Composable fun PageHeader(title: String,subtitle: String="",back: (() ->Unit)?=null) {
    if(back!=null) IconButton(back,Modifier.offset(x=(-12).dp)){Icon(Icons.Rounded.ArrowBack,"Retour")}
    BigTitle(title,subtitle)
}

@Composable fun PathScreen(model:AppModel, content:Content) {
    val p=model.progress
    val completed=p.completed
    var expanded by remember {mutableStateOf(content.chapters.indexOfFirst { c->c.lessons.any {it.id !in completed} }.coerceAtLeast(0))}
    val next=content.nextLesson(completed)
    val due=p.due(content)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column {Eyebrow("HAMIGO");Text("Salut ${p.name.split(' ').first()} !",fontSize=23.sp,fontWeight=FontWeight.ExtraBold)}
                Column(horizontalAlignment=Alignment.End) {Text("🔥 ${p.streak} jours",fontWeight=FontWeight.Bold,color=Color(0xFFB44D30));Text("⚡ ${p.xp} XP",fontSize=13.sp,color=Teal)}
            }
        }
        item {
            Panel(color=Mist) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Eyebrow("TA MISSION DU JOUR");Spacer(Modifier.height(10.dp));Text("Une petite onde,\nun grand déclic.",fontSize=26.sp,lineHeight=30.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(8.dp));Text("${p.todayXp} / ${p.dailyGoal} XP aujourd'hui",fontSize=13.sp,color=Muted)}
                    Pico(Modifier.size(112.dp))
                }
                LinearProgressIndicator(progress={ (p.todayXp.toFloat()/p.dailyGoal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(8.dp),color=Teal,trackColor=Color.White)
                Action(if(next==null)"Parcours terminé · refaire un défi" else "Continuer · ${next.title}") {
                    if(next!=null)model.startLesson(next) else model.route="practice"
                }
            }
        }
        item {
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
                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("%02d".format(index+1),Modifier.background(Color.White.copy(alpha=.18f),RoundedCornerShape(14.dp)).padding(12.dp),fontSize=21.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
                        Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)) {Text(c.title,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("${finished}/${c.lessons.size} · ${c.subtitle}",color=Color.White.copy(alpha=.85f),fontSize=12.sp)}
                        Icon(if(expanded==index)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null,tint=Color.White)
                    }
                }
                if(expanded==index) c.lessons.forEachIndexed { i,l ->
                    val done=l.id in completed;val active=l.id==next?.id
                    Row(Modifier.fillMaxWidth().padding(start=if(i%2==0)12.dp else 34.dp,end=if(i%2==0)34.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(onClick={model.startLesson(l)},modifier=Modifier.size(60.dp),shape=CircleShape,color=if(done)color else if(active)Gold else Color.White,shadowElevation=if(active)5.dp else 1.dp) {
                            Box(contentAlignment=Alignment.Center) {Icon(if(done)Icons.Rounded.Check else if(active)Icons.Rounded.PlayArrow else Icons.Rounded.RadioButtonUnchecked,l.title,tint=if(done)Color.White else color)}
                        }
                        Spacer(Modifier.width(15.dp))
                        Column(Modifier.weight(1f).clickable {model.startLesson(l)}.padding(vertical=13.dp)) {
                            Text(l.title,fontWeight=if(active)FontWeight.ExtraBold else FontWeight.Bold,fontSize=15.sp)
                            Text(if(done)"Signal reçu ✓" else if(active)"À toi de jouer · 3 à 5 min" else l.summary,maxLines=2,fontSize=12.sp,color=Muted)
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            PageHeader(lesson.title,lesson.summary){model.lesson=null}
            Row(verticalAlignment=Alignment.CenterVertically) {Pico(Modifier.size(70.dp));Spacer(Modifier.width(14.dp));Text("D'abord le déclic.\nEnsuite, à toi de jouer.",fontWeight=FontWeight.Bold,color=Teal)}
            lesson.body.forEach { Text(it,fontSize=17.sp,lineHeight=27.sp) }
            if(lesson.formula.isNotBlank()) Panel(color=Mist){Eyebrow("À RETENIR");Text(lesson.formula,fontSize=21.sp,fontWeight=FontWeight.Bold)}
            Text("Cours original Hamigo, adapté des ressources F6KGL. Les références sont dans les réglages.",fontSize=12.sp,color=Muted)
        }
        Action("C'est compris · ${lesson.questions.size} défis",Modifier.padding(20.dp)) {model.startQuestions(lesson.title,lesson.questions,lesson.id)}
    }
}

@Composable fun PracticeScreen(model:AppModel,content:Content) {
    var selected by remember {mutableStateOf(setOf<String>())}
    var section by remember {mutableStateOf("all")}
    var count by remember {mutableIntStateOf(10)}
    var search by remember {mutableStateOf("")}
    val pool=content.activeExam.filter {(section=="all" || it.section==section) && (selected.isEmpty() || it.topic in selected)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {BigTitle("À toi de jouer","Questions Exam1 d'entraînement, calculs et révisions.")}
        item {
            Panel(color=Color(0xFFFFE8E0)) {
                Eyebrow("MODE EXAMEN",Color(0xFFB65143));Text("Le grand contact",fontSize=23.sp,fontWeight=FontWeight.ExtraBold)
                Text("20 questions réglementation · 15 min\n20 questions technique · 30 min\nObjectif : au moins 10/20 dans chaque partie.",fontSize=14.sp,lineHeight=22.sp)
                Action("Lancer un examen blanc") {
                    val q=content.activeExam.filter{it.section=="regulation"}.shuffled().take(20)+content.activeExam.filter{it.section=="technique"}.shuffled().take(20)
                    model.startQuestions("Examen blanc",q,exam=true)
                }
            }
        }
        item {
            Panel {
                Text("Un mix à ta mesure",fontSize=21.sp,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "Tout","regulation" to "Réglementation","technique" to "Technique").forEach {(id,label)->
                        FilterChip(section==id,{section=id},label={Text(label,fontSize=11.sp)})
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Questions :",fontSize=13.sp);listOf(5,10,20).forEach { n->FilterChip(count==n,{count=n},label={Text("$n")}) }
                }
                Text(if(selected.isEmpty())"Tous les thèmes · ${pool.size} questions" else "${selected.size} thèmes · ${pool.size} questions",color=Muted,fontSize=12.sp)
                Action("Lancer le mix",enabled=pool.isNotEmpty()) {model.startQuestions("Mix radio",pool.shuffled().take(count))}
                OutlinedTextField(search,{search=it},label={Text("Chercher un thème")},singleLine=true,modifier=Modifier.fillMaxWidth())
            }
        }
        items(content.topics.keys.filter{it.contains(search,true)}) { topic ->
            Surface(onClick={selected=if(topic in selected)selected-topic else selected+topic},color=Color.White,shape=RoundedCornerShape(17.dp)) {
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(topic in selected,{checked->selected=if(checked)selected+topic else selected-topic})
                    Column(Modifier.weight(1f)) {Text(topic,fontSize=14.sp,fontWeight=FontWeight.Medium);Text("${content.topics[topic]?.size} questions",fontSize=11.sp,color=Muted)}
                }
            }
        }
        item {TextButton({selected=emptySet()}){Text("Effacer la sélection des thèmes")}}
        item {Panel(color=Mist){Text("Labo des calculs",fontSize=21.sp,fontWeight=FontWeight.Bold);Text("Des valeurs renouvelées : loi d'Ohm, puissance, longueur d'onde et cadran VHF.",color=Muted);Action("Ouvrir le labo"){model.startQuestions("Labo des calculs",PracticeGenerator.create())}}}
        item {Text("Banque REF Exam1 : ${content.exam.size} questions archivées, ${content.excluded.size} entrées historiques ou incohérentes écartées des séances. Chaque question garde sa référence et son illustration.",fontSize=12.sp,color=Muted)}
    }
}

@Composable fun ResourcesScreen(model:AppModel,content:Content) {
    var search by remember {mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {BigTitle("Les petits mémos","Le savoir radio, toujours dans ta poche.")}
        item {OutlinedTextField(search,{search=it},label={Text("Morse, résistances, codes Q…")},leadingIcon={Icon(Icons.Rounded.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        items(content.references.filter {it.title.contains(search,true) || it.rows.any {r->r.term.contains(search,true)||r.description.contains(search,true)}}) {cat ->
            Surface(onClick={model.resource=cat},color=Color.White,shape=RoundedCornerShape(23.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(Mist,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {Icon(Icons.Rounded.Style,null,tint=Teal)}
                    Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) {Text(cat.title,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("${cat.rows.size} repères · ${if(cat.flashcards)"flashcards" else "fiche pratique"}",fontSize=12.sp,color=Muted)}
                    Icon(Icons.Rounded.ChevronRight,null)
                }
            }
        }
    }
}

@Composable fun ReferenceScreen(model:AppModel,cat:RefCategory) {
    var search by remember {mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {PageHeader(cat.title,cat.subtitle){model.resource=null}}
        if(cat.flashcards) item {Action("Réviser avec les flashcards") {
            val cards=model.content!!.flashcards.filter {it.topic==cat.id}
            val reviews=model.progress.reviews
            val selected=cards.sortedWith(compareBy<Question>{(reviews[it.id]?.due ?: 0L)>System.currentTimeMillis()}.thenBy{reviews[it.id]?.due ?: 0L}).take(12)
            model.startQuestions(cat.title,selected)
        }}
        item {OutlinedTextField(search,{search=it},label={Text("Filtrer cette fiche")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        items(cat.rows.filter{it.term.contains(search,true)||it.description.contains(search,true)}) {row ->
            Panel {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(row.term,fontSize=20.sp,fontWeight=FontWeight.ExtraBold,color=Teal,modifier=Modifier.weight(1f))
                    if(cat.id.contains("morse",true)) IconButton({playMorse(row.description)}){Icon(Icons.Rounded.VolumeUp,"Écouter le Morse")}
                }
                Text(row.description,fontSize=16.sp,lineHeight=24.sp)
                if(row.extra.isNotBlank())Text(row.extra,fontSize=12.sp,color=Muted,lineHeight=18.sp)
            }
        }
    }
}

@Composable fun ProfileScreen(model:AppModel,content:Content) {
    val p=model.progress
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){BigTitle(p.name,"Un peu chaque jour, beaucoup à l'arrivée.")};IconButton({model.route="settings"}){Icon(Icons.Rounded.Settings,"Réglages")}}}
        item {Panel(color=Mist){Row(verticalAlignment=Alignment.CenterVertically){Pico(Modifier.size(100.dp));Column(Modifier.weight(1f)){Text("Niveau ${1+p.xp/250}",fontSize=26.sp,fontWeight=FontWeight.ExtraBold);Text("${p.xp} XP · 🔥 ${p.streak} jours",color=Teal,fontWeight=FontWeight.Bold)}};LinearProgressIndicator(progress={(p.xp%250)/250f},modifier=Modifier.fillMaxWidth(),color=Teal,trackColor=Color.White);Text("${250-p.xp%250} XP avant le prochain niveau",fontSize=12.sp,color=Muted)}}
        item {
            Panel {
                Text("Cette semaine",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth().height(100.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
                    val days=(6L downTo 0L).map{LocalDate.now().minusDays(it)}
                    val maximum=days.maxOf{p.dayXp(it)}.coerceAtLeast(p.dailyGoal)
                    days.forEach {day->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${p.dayXp(day)}",fontSize=10.sp,color=Muted)
                        Box(Modifier.fillMaxWidth().height((p.dayXp(day).toFloat()/maximum*62+4).dp).background(if(day==LocalDate.now())Coral else Teal,RoundedCornerShape(5.dp)))
                        Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,java.util.Locale.FRENCH),fontSize=11.sp)
                    }}
                }
                Text("${p.weeklyXp} XP en 7 jours · objectif ${p.dailyGoal} XP/jour",fontSize=12.sp,color=Muted)
            }
        }
        item {Panel {Text("Ton signal se renforce",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("${p.completed.size} / ${content.lessons.size} leçons terminées\n${p.reviews.values.count{it.repetitions>=3}} notions consolidées\n${p.totalAnswers} réponses · ${if(p.totalAnswers==0)0 else p.totalCorrect*100/p.totalAnswers}% de réussite",lineHeight=26.sp);Action("Partager ma progression"){NativeShare.progressImage(context,p.snapshot())}}}
        item {Panel {Text("La mémoire aime les retrouvailles",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("Les bonnes réponses reviennent après 1 jour, puis 6 jours, puis plus loin selon ta facilité. Les erreurs reviennent après 10 minutes. Les flashcards te laissent choisir leur difficulté.",fontSize=14.sp,color=Muted,lineHeight=22.sp)}}
    }
}
