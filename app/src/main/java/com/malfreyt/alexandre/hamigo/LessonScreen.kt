package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
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
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Pico(Modifier.size(64.dp),mood=MascotMood.THINKING,pose=MascotPose.POINT)
                    Spacer(Modifier.width(12.dp))
                    Text(if(model.lessonPreviewOnly)"Les notions du cours,\nà relire à ton rythme." else "D'abord le déclic.\nEnsuite, à toi de jouer.",fontWeight=FontWeight.Bold,color=Teal)
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
        if(!model.lessonPreviewOnly) Action("À toi de jouer · ${lesson.questions.size} à ${lesson.questions.size+2} défis",Modifier.padding(16.dp)) {
            model.startQuestions(lesson.title,LessonSessionBuilder.create(lesson),lesson.id)
        }
    }
}
