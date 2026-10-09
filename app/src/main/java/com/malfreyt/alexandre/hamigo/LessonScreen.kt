package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.util.WeakHashMap

private object LessonIntroNavigation {
    private data class Entry(val id:String,val opening:Int,val list:LazyListState=LazyListState())
    private val states=WeakHashMap<AppModel,Entry>()
    fun state(model:AppModel,lesson:Lesson):LazyListState {
        val old=states[model]
        if(old!=null && old.id==lesson.id && old.opening==model.lessonOpening) return old.list
        return Entry(lesson.id,model.lessonOpening).also {states[model]=it}.list
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun LessonScreen(model:AppModel,lesson:Lesson,returnTarget:MemoReturnTarget?=null) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val speech=remember(context,lesson.id,returnTarget) {if(returnTarget==null)LessonSpeech(context) else null}
    val companion=rememberPicoCompanion("lesson-${lesson.id}-${model.lessonOpening}",enabled=returnTarget==null)
    val touch=rememberPicoNarrationTap("lesson-${lesson.id}-${model.lessonOpening}")
    DisposableEffect(speech,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)speech?.stop()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);speech?.close()}
    }
    val saved=remember(model,lesson.id,model.lessonOpening) {LessonIntroNavigation.state(model,lesson)}
    val list=remember(saved,returnTarget) {if(returnTarget==null)saved else LazyListState(saved.firstVisibleItemIndex,saved.firstVisibleItemScrollOffset)}
    val references=model.content?.referencesFor(lesson).orEmpty()
    var viewport by remember {mutableStateOf<Rect?>(null)}
    DisposableEffect(returnTarget) {
        returnTarget?.let { target ->
            val index=references.indexOfFirst { it.id==target.categoryId }
            target.attach(reveal={
                if(index<0) null else {
                    val visible=snapshotFlow {viewport}.filterNotNull().first()
                    val row=target.rowBounds
                    if(row==null || row.top<visible.top || row.bottom>visible.bottom) {
                        target.rowBounds=null
                        list.scrollToItem(2+lesson.body.size+(if(lesson.formula.isNotBlank())1 else 0)+index)
                    }
                    snapshotFlow {target.rowBounds}.filterNotNull().filter {it.top>=visible.top && it.bottom<=visible.bottom}.first()
                }
            },commit={saved.requestScrollToItem(list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset)})
        }
        onDispose { }
    }
    Column(Modifier.fillMaxSize().testTag("lesson-introduction")) {
        LazyColumn(Modifier.weight(1f).testTag("lesson-introduction-list").onGloballyPositioned {viewport=it.boundsInWindow()},state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {PageHeader(lesson.title,lesson.summary){model.lesson=null}}
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val picoSize=if(maxWidth<340.dp)108.dp else 124.dp
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TalkingPico(Modifier.size(picoSize).alignBy(PicoBodyCenter).testTag("lesson-pico")
                        .semantics {contentDescription=if(speech?.reading==true)"Arrêter la lecture du cours avec Pico" else "Écouter le cours avec Pico";stateDescription=if(touch.closedEyes)"Pico ferme les yeux" else if(touch.playful)"Pico fait le clown" else if(speech?.speaking==true)"Pico parle" else companion.mood.description}
                        .clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,enabled=returnTarget==null) {touch.tap(android.os.SystemClock.uptimeMillis(),{speech?.toggle(lesson)},{speech?.stop()})},talking=speech?.speaking==true,
                        mood=if(touch.playful)touch.mood else if(speech?.reading==true)MascotMood.HAPPY else companion.mood,pose=if(touch.playful)MascotPose.HUG else companion.pose,mirrored=true,pointLeft=true,eyesClosed=touch.closedEyes,idleMotion=companion.active,reaction=if(touch.playful)touch.burst else 0)
                    Spacer(Modifier.width(12.dp))
                    Text(if(model.lessonPreviewOnly)"Les notions du cours,\nà relire à ton rythme." else "D'abord le déclic.\nEnsuite, à toi de jouer.",Modifier.weight(1f).alignBy {it.measuredHeight/2},fontWeight=FontWeight.Bold,color=Teal)
                    IconButton({touch.audio {speech?.toggle(lesson)}},Modifier.size(48.dp).alignBy {it.measuredHeight/2}.testTag("lesson-listen"),enabled=speech!=null) {
                        Icon(if(speech?.reading==true)Icons.Rounded.Stop else Icons.Rounded.VolumeUp,if(speech?.reading==true)"Arrêter la lecture du cours" else "Écouter le cours",tint=Teal)
                    }
                }
                }
            }
            lesson.body.forEachIndexed { index,paragraph -> item {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    MorseAwareText(paragraph,fontSize=16.sp,lineHeight=24.sp)
                    lesson.visuals.getOrNull(index)?.takeIf {it.isNotBlank()}?.let {visual -> Panel(color=Color.White) {LogicLearningVisual(visual,showNames=false,showCaption=false)} }
                }
            } }
            if(lesson.formula.isNotBlank()) item {Panel(color=Mist){Eyebrow("À RETENIR");MorseAwareText(lesson.formula,fontSize=21.sp,fontWeight=FontWeight.Bold)}}
            items(references,key={it.id}) { category ->
                Surface(onClick={if(returnTarget==null)model.resource=category},modifier=Modifier.testTag("lesson-memo-${category.id}").onGloballyPositioned {
                    if(returnTarget?.categoryId==category.id) {returnTarget.rowBounds=it.boundsInWindow();returnTarget.fullRowHeight=it.size.height}
                },color=Color.White,shape=RoundedCornerShape(18.dp)) {MemoMorphRow(category)}
            }
        }
        if(!model.lessonPreviewOnly) Action("À toi de jouer",Modifier.padding(16.dp)) {
            model.startQuestions(lesson.title,LessonSessionBuilder.create(lesson),lesson.id)
        }
    }
}
