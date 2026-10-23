package com.malfreyt.alexandre.hamigo

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset

internal const val HamigoWelcomeLesson="l-hamigo-bienvenue"

/** Local guidance never records mastery and never enters the cloud progress document. */
internal class HamigoTourState(private val prefs:SharedPreferences) {
    var step by mutableIntStateOf(run {
        val old=prefs.getInt("hamigoTourStep",-1)
        if(prefs.getInt("hamigoTourVersion",1)<2&&old>=3)3 else old.takeIf {it in -1..8} ?: -1
    });private set
    var skipRequested by mutableStateOf(false);private set
    val active get()=step>=0
    val navigationPhase get()=step in 3..7
    val destination get()=listOf("practice","resources","friends","profile","path").getOrNull(step-3)
    val currentRoute get()=listOf("path","practice","resources","friends","profile").getOrNull(step-3)
    fun start(){cancelSkip();move(0)}
    fun move(value:Int){require(value in -1..8);step=value;prefs.edit().putInt("hamigoTourVersion",2).putInt("hamigoTourStep",value).apply()}
    fun requestSkip(){if(active)skipRequested=true}
    fun cancelSkip(){skipRequested=false}
    fun finish(){cancelSkip();move(-1)}
}

internal class HamigoTourTargets {
    val bounds=mutableStateMapOf<String,Rect>()
    var cardHeight by mutableIntStateOf(0)
}
internal val LocalHamigoTourTargets=staticCompositionLocalOf<HamigoTourTargets?> {null}
internal val LocalTourNavigationGuard=staticCompositionLocalOf<()->Boolean> {{true}}
@Composable internal fun tourNavigation(action:()->Unit):()->Unit {
    val allow=LocalTourNavigationGuard.current
    return feedbackClick {if(allow())action()}
}
@Composable internal fun Modifier.hamigoTourTarget(name:String):Modifier {
    val targets=LocalHamigoTourTargets.current ?: return this
    return onGloballyPositioned {targets.bounds[name]=it.boundsInWindow()}
}
@Composable internal fun tourContentClearance(model:AppModel)=if(model.tour.navigationPhase)
    with(LocalDensity.current){(LocalHamigoTourTargets.current?.cardHeight ?: 0).toDp()}+64.dp else 0.dp

@Composable private fun TourActions(model:AppModel,compact:Boolean=false) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val skipWidth=(maxWidth-64.dp).coerceAtLeast(48.dp)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        TextButton(feedbackClick {model.tour.requestSkip()},Modifier.testTag("hamigo-tour-skip-all").then(if(compact)Modifier.widthIn(max=skipWidth) else Modifier),contentPadding=PaddingValues(horizontal=if(compact)4.dp else 8.dp)) {
            Text("Passer le tutoriel",fontSize=if(compact)11.sp else 12.sp,color=MaterialTheme.colorScheme.error)
        }
        TextButton(feedbackClick {model.nextTourStep()},Modifier.testTag("hamigo-tour-next").widthIn(min=56.dp),contentPadding=PaddingValues(horizontal=8.dp)) {
            Text("Suivant",fontSize=12.sp,fontWeight=FontWeight.Bold)
        }
    }
    }
}

/** The small companion overlaps its card; only this help area reserves that overlap. */
@Composable internal fun TourLessonHint(model:AppModel,modifier:Modifier=Modifier) {
    if(model.tour.step!=1||model.lesson?.id!=HamigoWelcomeLesson)return
    BoxWithConstraints(modifier.testTag("hamigo-tour-lesson-hint")) {
        val concise=maxHeight<180.dp
        Surface(Modifier.padding(top=20.dp),color=Mist,shape=RoundedCornerShape(18.dp),shadowElevation=4.dp) {
            Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
                    Text(if(concise)"Lis, puis joue" else "Lis, puis à toi de jouer",Modifier.padding(end=64.dp),fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,color=Teal)
                    if(!concise)Text("Découvre l’introduction, puis essaie les exercices de ce premier cours.",Modifier.padding(top=6.dp),fontSize=12.sp,lineHeight=17.sp)
                }
                TourActions(model,compact=true)
            }
        }
        Pico(Modifier.align(Alignment.TopEnd).padding(end=8.dp).size(56.dp).testTag("hamigo-tour-lesson-pico"),mood=MascotMood.HAPPY,pose=MascotPose.WAVE)
    }
}

@Composable private fun TourSkipConfirmation(model:AppModel) {
    if(!model.tour.skipRequested)return
    AlertDialog(onDismissRequest={model.tour.cancelSkip()},title={Text("Passer le tutoriel ?")},
        text={Text("Tu passes directement au dernier message de Pico, sur Parcours. Ta progression est conservée et tu pourras reprendre la visite depuis les paramètres.")},
        confirmButton={TextButton(feedbackClick(model::skipTourToWelcome),Modifier.testTag("hamigo-tour-confirm-skip")){Text("Passer le tutoriel",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(feedbackClick {model.tour.cancelSkip()},Modifier.testTag("hamigo-tour-continue-guide")){Text("Continuer la visite")}})
}

@Composable internal fun HamigoTourOverlay(model:AppModel,targets:HamigoTourTargets) {
    val tour=model.tour
    TourSkipConfirmation(model)
    if(!tour.active||model.showWelcome||model.debugToolsOpen||tour.step==1||tour.step==2&&model.session?.done!=true)return
    var root by remember {mutableStateOf(Rect.Zero)}
    val targetName=when(tour.step){0->"intro";2->"tour-return-path";in 3..7->"nav-${tour.destination}";else->null}
    val target=targetName?.let {targets.bounds[it]}
    val progress=remember(tour.step){Animatable(0f)}
    val context=LocalContext.current
    val density=LocalDensity.current
    val animate=remember(context){android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}
    LaunchedEffect(tour.step,target!=null){if(target!=null){progress.snapTo(0f);progress.animateTo(1f,tween(if(animate)480 else 0))}}
    val texts=when(tour.step) {
        0->"Ton parcours" to "Tu es sur Parcours : suis les cours dans l’ordre pour préparer le certificat radioamateur. Commençons par l’introduction à Hamigo."
        2->"Ton premier cours est terminé" to "Les corrections t’aident à comprendre tes erreurs. Reviens maintenant au Parcours avec le bouton ci-dessous."
        3->"De retour sur Parcours" to "Tu retrouveras ici tous les cours dans l’ordre conseillé. Allons voir Défis pour découvrir les entraînements et les révisions."
        4->"Défis" to "Ici : examens blancs, mix sur mesure et révisions des notions étudiées. Passe à Mémo pour retrouver les connaissances et les outils."
        5->"Mémo" to "Les fiches, les flashcards, la calculatrice et le brouillon restent à portée de main. Découvrons ensuite la page Équipe."
        6->"Équipe" to "Retrouve tes amis et comparez vos progrès. La connexion GitHub est facultative. Passe à Moi pour voir ton bilan personnel."
        7->"Moi" to "Suis ton activité, tes XP et tes progrès vers le certificat et l’exploration. Reviens au Parcours pour terminer la visite."
        else->"Le parcours est à toi !" to "Avance à ton rythme, entraîne-toi dans Défis et retrouve les notions utiles dans Mémo. Pico t’accompagne jusqu’au certificat."
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("hamigo-tour").onGloballyPositioned {root=it.boundsInWindow()}) {
        val raw=target?.let {it.translate(-root.left,-root.top)}
        val end=raw?.intersect(Rect(0f,0f,root.width,root.height))?.takeIf {it.width>0f&&it.height>0f}
        // Keep a small halo around the complete lesson row. Only the original row
        // receives touches: adjacent chapters remain blocked even when the light reaches them.
        val spotlight=end?.let {r->if(tour.step==0) {
            val breathingRoom=with(density){8.dp.toPx()}
            val halfHeight=maxOf(r.height/2f+breathingRoom,with(density){52.dp.toPx()})
            Rect((r.left-with(density){3.dp.toPx()}).coerceAtLeast(0f),(r.center.y-halfHeight).coerceAtLeast(0f),
                (r.right+with(density){3.dp.toPx()}).coerceAtMost(root.width),(r.center.y+halfHeight).coerceAtMost(root.height))
        } else r}
        val hole=spotlight?.let {r->val t=progress.value;Rect(r.left*t,r.top*t,root.width+(r.right-root.width)*t,root.height+(r.bottom-root.height)*t)}
        if(tour.step==0)spotlight?.let {r->Box(Modifier.offset {IntOffset(r.left.toInt(),r.top.toInt())}
            .size(with(density){r.width.toDp()},with(density){r.height.toDp()}).testTag("hamigo-tour-intro-highlight"))}
        if(tour.step==0||tour.step==8) {
            Canvas(Modifier.fillMaxSize().testTag("hamigo-tour-veil")) {
                val veil=Path().apply {fillType=PathFillType.EvenOdd;addRect(Rect(0f,0f,size.width,size.height));hole?.takeIf {tour.step==0}?.let {addRoundRect(RoundRect(it,CornerRadius(20.dp.toPx())))}}
                drawPath(veil,Ink.copy(alpha=if(tour.step==8).22f else .72f))
            }
            @Composable fun Block(rect:Rect) {
                if(rect.width>0f&&rect.height>0f)Box(Modifier.offset {IntOffset(rect.left.toInt(),rect.top.toInt())}
                    .size(with(density){rect.width.toDp()},with(density){rect.height.toDp()})
                    .clearAndSetSemantics {}.pointerInput(Unit) {awaitPointerEventScope {while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}})
            }
            if(tour.step==8||end==null)Block(Rect(0f,0f,root.width,root.height)) else {
                Block(Rect(0f,0f,root.width,end.top));Block(Rect(0f,end.bottom,root.width,root.height))
                Block(Rect(0f,end.top,end.left,end.bottom));Block(Rect(end.right,end.top,root.width,end.bottom))
            }
        }
        val short=maxHeight<480.dp
        val final=tour.step==8
        val first=tour.step==0
        val bottomSpace=if(first||final)0.dp else end?.let {with(density){(root.height-it.top).coerceAtLeast(0f).toDp()}+(if(tour.navigationPhase)52.dp else 8.dp)} ?: 120.dp
        val beside=first&&short&&maxWidth>=560.dp
        // The actual lesson bounds, safe inset and gap cap the bubble before it can cover its target.
        val firstRoom=end?.let {with(density){it.top.toDp()}-with(density){WindowInsets.safeDrawing.getTop(this).toDp()}-24.dp} ?: 320.dp
        val cardHeight=when {first&&!beside->minOf(if(short)220.dp else 320.dp,firstRoom.coerceAtLeast(128.dp));short->220.dp;final->440.dp;else->330.dp}
        val compactCard=short||first&&cardHeight<270.dp
        val cardAlignment=if(beside)Alignment.TopEnd else if(first)Alignment.TopCenter else if(final)Alignment.Center else Alignment.BottomCenter
        Surface(Modifier.align(cardAlignment).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))
            .padding(start=16.dp,end=16.dp,top=if(first)12.dp else 0.dp,bottom=bottomSpace)
            .widthIn(max=if(beside)maxWidth*.44f else if(short)560.dp else 480.dp).heightIn(max=cardHeight)
            .onSizeChanged {if(tour.navigationPhase)targets.cardHeight=it.height}.testTag("hamigo-tour-card"),
            color=Cream,shape=RoundedCornerShape(24.dp),shadowElevation=8.dp) {
            Column(Modifier.padding(if(compactCard)12.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(if(compactCard)5.dp else 8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                if(final&&short)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Pico(Modifier.size(88.dp).testTag("hamigo-tour-final-pico"),mood=MascotMood.HAPPY,pose=MascotPose.WAVE)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(texts.first,fontSize=18.sp,lineHeight=22.sp,fontWeight=FontWeight.ExtraBold)
                        Text(texts.second,fontSize=14.sp,lineHeight=20.sp)
                        Button(feedbackClick {tour.finish()},Modifier.testTag("hamigo-tour-finish").heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)) {Text("Commencer",fontWeight=FontWeight.Bold)}
                    }
                } else {
                if(final)Pico(Modifier.size(112.dp).testTag("hamigo-tour-final-pico"),mood=MascotMood.HAPPY,pose=MascotPose.WAVE)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(!final)Pico(Modifier.size(if(compactCard)44.dp else 56.dp),mood=MascotMood.HAPPY,pose=MascotPose.WAVE)
                    Text(texts.first,Modifier.weight(1f),fontSize=if(compactCard)17.sp else 20.sp,lineHeight=24.sp,fontWeight=FontWeight.ExtraBold)
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {Text(texts.second,fontSize=14.sp,lineHeight=20.sp)}
                if(final)Button(feedbackClick {tour.finish()},Modifier.testTag("hamigo-tour-finish").heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)) {Text("Commencer",fontWeight=FontWeight.Bold)} else TourActions(model)
                }
            }
        }
        if(tour.navigationPhase&&end!=null) {
            val attention=if(animate) {
                val transition=rememberInfiniteTransition(label="tour-tab-attention")
                val value by transition.animateFloat(0f,1f,infiniteRepeatable(tween(850),RepeatMode.Reverse),label="tour-tab-bounce")
                value
            } else 0f
            Surface(Modifier.offset {IntOffset((end.center.x-with(density){18.dp.toPx()}).toInt(),
                (end.top-with(density){44.dp.toPx()}+with(density){4.dp.toPx()}*attention).toInt())}
                .size(36.dp).testTag("hamigo-tour-tab-arrow").semantics {contentDescription="Onglet suivant : "+textsForRoute(tour.destination)},
                color=Gold,shape=RoundedCornerShape(14.dp),shadowElevation=2.dp) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val arrow=Path().apply {
                        moveTo(size.width*.25f,size.height*.12f)
                        cubicTo(size.width*.68f,size.height*.12f,size.width*.58f,size.height*.48f,size.width*.58f,size.height*.85f)
                        moveTo(size.width*.25f,size.height*.54f)
                        lineTo(size.width*.58f,size.height*.87f)
                        lineTo(size.width*.9f,size.height*.54f)
                    }
                    drawPath(arrow,Ink,style=Stroke(width=2.5.dp.toPx(),cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
    }
}

private fun textsForRoute(route:String?)=when(route) {"practice"->"Défis";"resources"->"Mémo";"friends"->"Équipe";"profile"->"Moi";else->"Parcours"}
