package com.malfreyt.alexandre.hamigo

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface PathEntry {
    val key:String
    data class Section(val section:CourseSection,val first:Boolean):PathEntry {override val key="section-"+section.id}
    data class ChapterGroup(val chapter:Chapter,val index:Int,val expanded:Boolean):PathEntry {override val key="chapter-"+chapter.id}
}
private data class PathAnchor(val key:String,val fraction:Float?,val offset:Int?,val size:Int,val request:Long)

/** Position the measured row in the viewport, below the sticky header and above floating controls. */
internal suspend fun LazyListState.anchorPathItem(index:Int,key:String,fraction:Float,topOcclusion:Int,bottomOcclusion:Int,
                                                animate:Boolean=true,sizeHint:Int=0,targetOffset:Int=0,targetSize:Int?=null) {
    fun desiredTop(itemSize:Int):Int {
        val layout=layoutInfo
        val first=layout.viewportStartOffset+topOcclusion
        val size=targetSize ?: itemSize
        val last=(layout.viewportEndOffset-bottomOcclusion-size).coerceAtLeast(first)
        val center=layout.viewportStartOffset+(layout.viewportEndOffset-layout.viewportStartOffset)*fraction.coerceIn(0f,1f)
        return (center-size/2f).roundToInt().coerceIn(first,last)-targetOffset
    }
    val visible=layoutInfo.visibleItemsInfo.firstOrNull {it.key==key}
    val measured=visible?.size ?: sizeHint
    if(!animate)scrollToItem(index,-desiredTop(measured))
    else if(visible!=null)animateScrollBy((visible.offset-desiredTop(measured)).toFloat(),tween(350))
    else animateScrollToItem(index,-desiredTop(measured))
    repeat(2) {
        withFrameNanos { }
        val item=layoutInfo.visibleItemsInfo.firstOrNull {it.key==key} ?: return
        val difference=(item.offset-desiredTop(item.size)).toFloat()
        if(abs(difference)<=1f)return
        if(animate)animateScrollBy(difference,tween(220)) else scrollToItem(index,-desiredTop(item.size))
    }
}

/** Keep the parent chapter and its scroll while reading a course; nothing enters a backup. */
internal class PathNavigationState(initialChapter:String?) {
    val expanded=mutableStateOf(initialChapter)
    val list=LazyListState()
    val requestedLesson=mutableStateOf<String?>(null)
    fun revealLesson(chapterId:String,lessonId:String) {expanded.value=chapterId;requestedLesson.value=lessonId}
}

/** Stable keys keep the chapter under the finger when another group closes above it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun PathScreen(model:AppModel,content:Content) {
    val p=model.displayedProgress ?: model.progress
    val completed=p.completed
    val ignored=model.ignoredLessons
    val pathCompleted=model.pathCompletedLessons
    val started=remember(p,content,model.revision) {
        val answered=p.reviews.keys
        completed+content.lessons.filter {lesson->lesson.questions.any {it.id in answered}}.map {it.id}
    }
    val next=content.nextLesson(pathCompleted)
    val nextChapter=content.chapters.firstOrNull {c->c.lessons.any {it.id==next?.id}}
    // A LazyListState can retain its composition, and therefore the model. Keep it
    // on that model rather than in a static weak-key map whose values reach its keys.
    val navigation=remember(model){model.pathNavigationState ?: PathNavigationState(nextChapter?.id ?: content.chapters.firstOrNull()?.id)
        .also {model.pathNavigationState=it}}
    var expanded by navigation.expanded
    var anchor by remember {mutableStateOf<PathAnchor?>(null)}
    var headerHeight by remember {mutableIntStateOf(0)}
    var viewportHeight by remember {mutableIntStateOf(0)}
    var guideAnchored by remember {mutableStateOf(false)}
    val measuredRows=remember {mutableStateMapOf<String,Int>()}
    val guideFirst=model.tour.active&&model.tour.step==0
    val tourTargets=LocalHamigoTourTargets.current
    val due=remember(content,p,model.revision){p.due(content)}
    val listState=navigation.list
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    val tourClearance=tourContentClearance(model)
    val entries=remember(content,expanded) {
        buildList<PathEntry> {
            val emitted=mutableSetOf<String>()
            content.chapters.forEachIndexed {index,chapter->
                content.sections.firstOrNull {it.id==chapter.sectionId}?.takeIf {emitted.add(it.id)}?.let {
                    add(PathEntry.Section(it,emitted.size==1))
                }
                add(PathEntry.ChapterGroup(chapter,index,chapter.id==expanded))
            }
        }
    }
    val prefixCount=2+(if(due.isNotEmpty())1 else 0)+(if(guideFirst)1 else 0)
    fun parentKey(key:String)=if(key.startsWith("lesson-"))content.chapters.firstOrNull {chapter->chapter.lessons.any {"lesson-"+it.id==key}}?.let {"chapter-"+it.id} else key
    fun indexOf(key:String)=entries.indexOfFirst {it.key==parentKey(key)}.takeIf {it>=0}?.plus(prefixCount)
    val bottomOcclusion=with(density){(76.dp+LocalNavigationContentOverlap.current).roundToPx()}
    val rowGap=with(density){12.dp.roundToPx()}
    fun measuredLesson(key:String):Pair<Int,Int>? {
        val group=entries.filterIsInstance<PathEntry.ChapterGroup>().firstOrNull {it.expanded&&it.key==parentKey(key)} ?: return null
        val preceding=group.chapter.lessons.takeWhile {"lesson-"+it.id!=key}
        val height=measuredRows[key] ?: return null
        var offset=measuredRows[group.key] ?: return null
        for(lesson in preceding)offset+=(measuredRows["lesson-"+lesson.id] ?: return null)+rowGap
        return offset+rowGap to height
    }
    suspend fun anchorLesson(index:Int,key:String,fraction:Float,animate:Boolean=true,sizeHint:Int=0) {
        if(listState.layoutInfo.visibleItemsInfo.none {it.key==parentKey(key)}) {
            if(animate)listState.animateScrollToItem(index) else listState.scrollToItem(index)
        }
        snapshotFlow {measuredLesson(key)}.first {it!=null}
        val measured=measuredLesson(key) ?: return
        listState.anchorPathItem(index,parentKey(key) ?: return,fraction,headerHeight,bottomOcclusion,
            animate=animate,sizeHint=sizeHint,targetOffset=measured.first,targetSize=measured.second)
    }

    // A temporary leading reserve lets even the first lesson reach the thumb area on tall windows.
    // Expose the guide target only after its final measured position is available.
    LaunchedEffect(guideFirst,expanded,headerHeight,viewportHeight,entries) {
        if(!guideFirst){guideAnchored=false;return@LaunchedEffect}
        guideAnchored=false
        tourTargets?.bounds?.remove("intro")
        if(headerHeight==0||viewportHeight==0)return@LaunchedEffect
        val introChapter=content.chapters.firstOrNull {c->c.lessons.any {it.id==HamigoWelcomeLesson}} ?: return@LaunchedEffect
        if(expanded!=introChapter.id) {anchor=null;expanded=introChapter.id;return@LaunchedEffect}
        snapshotFlow {listState.layoutInfo.totalItemsCount}.first {it==prefixCount+entries.size+1}
        indexOf("lesson-$HamigoWelcomeLesson")?.let {index->
            anchorLesson(index,"lesson-$HamigoWelcomeLesson",.72f,animate=false)
            guideAnchored=true
        }
    }

    LaunchedEffect(navigation.requestedLesson.value,headerHeight,viewportHeight) {
        val requested=navigation.requestedLesson.value ?: return@LaunchedEffect
        if(headerHeight==0||viewportHeight==0||guideFirst)return@LaunchedEffect
        anchor=PathAnchor("lesson-"+requested,.5f,null,0,System.nanoTime())
        navigation.requestedLesson.value=null
    }

    // Measure the whole expanded group, including its lessons, before centring its actual bounds.
    LaunchedEffect(anchor,expanded,entries,headerHeight,viewportHeight) {
        val request=anchor ?: return@LaunchedEffect
        if(guideFirst||headerHeight==0||viewportHeight==0)return@LaunchedEffect
        snapshotFlow {listState.layoutInfo.totalItemsCount}.first {it==prefixCount+entries.size+1}
        withFrameNanos { }
        indexOf(request.key)?.let {index->
            if(listState.layoutInfo.visibleItemsInfo.none {it.key==parentKey(request.key)})request.offset?.let {listState.scrollToItem(index,-it)}
            request.fraction?.let {fraction->
                if(request.key.startsWith("lesson-"))anchorLesson(index,request.key,fraction,sizeHint=request.size)
                else listState.anchorPathItem(index,request.key,fraction,headerHeight,bottomOcclusion,sizeHint=request.size)
            }
        }
        if(anchor==request)anchor=null
    }
    val nextVisible by remember(next?.id,expanded,headerHeight,density,entries) {
        derivedStateOf {
            val measured=measuredLesson("lesson-"+next?.id)
            val group=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key=="chapter-"+nextChapter?.id}
            measured!=null&&group!=null&&group.offset+measured.first+measured.second>headerHeight&&
                group.offset+measured.first<listState.layoutInfo.viewportEndOffset-bottomOcclusion
        }
    }
    val nearTop by remember(density) {
        derivedStateOf {!listState.canScrollBackward||listState.firstVisibleItemIndex<=1&&listState.firstVisibleItemScrollOffset<=with(density){48.dp.roundToPx()}}
    }
    val nextAbove by remember(entries,next?.id,nextChapter?.id) {
        derivedStateOf {
            val key="chapter-"+nextChapter?.id
            val visible=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key==key}
            val measured=measuredLesson("lesson-"+next?.id)
            visible?.let {it.offset+(measured?.let {row->row.first+row.second/2f} ?: it.size/2f)<(listState.layoutInfo.viewportStartOffset+listState.layoutInfo.viewportEndOffset)/2f}
                ?: (indexOf(key)?.let {it<listState.firstVisibleItemIndex} ?: false)
        }
    }
    val nextCentered by remember(next?.id,entries,density) {
        derivedStateOf {
            val row=measuredLesson("lesson-"+next?.id)
            val group=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key=="chapter-"+nextChapter?.id}
            row!=null&&group!=null&&abs(group.offset+row.first+row.second/2f-
                (listState.layoutInfo.viewportStartOffset+listState.layoutInfo.viewportEndOffset)/2f)<with(density){20.dp.toPx()}
        }
    }
    val pointsUp=next==null||nextCentered||nextAbove
    var previousDirection by remember {mutableStateOf(pointsUp)}
    var rotationTarget by remember {mutableFloatStateOf(if(pointsUp)0f else 180f)}
    LaunchedEffect(pointsUp) {
        if(pointsUp!=previousDirection){rotationTarget+=180f;previousDirection=pointsUp}
    }
    // A growing target gives every change the same clockwise direction; Compose honors duration scale.
    val rotation by animateFloatAsState(rotationTarget,tween(320),label="path-navigation-arrow")
    Box(Modifier.fillMaxSize().onSizeChanged {viewportHeight=it.height}) {
        LazyColumn(Modifier.fillMaxSize().testTag("path-list"),state=listState,
            contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=104.dp+LocalNavigationContentOverlap.current+tourClearance),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            stickyHeader(key="path-header") {
                Row(Modifier.fillMaxWidth().testTag("path-sticky-header").onSizeChanged {headerHeight=it.height}.stickyHeaderShadow(listState).background(Cream).padding(vertical=8.dp),
                    horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Eyebrow("HAMIGO");Text("Salut "+p.name.split(' ').first()+" !",fontSize=23.sp,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)}
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Mist.copy(alpha=.6f))
                        .clickable(role=Role.Button,onClick=feedbackClick {model.route="profile"})
                        .semantics(mergeDescendants=true) {contentDescription="Voir ma progression : "+dayCount(p.streak)+" de série, "+p.xp+" XP"}
                        .padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        Column(horizontalAlignment=Alignment.End) {StatMetric(dayCount(p.streak),Icons.Rounded.LocalFireDepartment,Color(0xFFB44D30));StatMetric(p.xp.toString()+" XP",Icons.Rounded.Bolt,fontSize=13.sp)}
                        Icon(Icons.Rounded.ChevronRight,null,Modifier.size(17.dp),tint=Muted)
                    }
                }
            }
            if(guideFirst)item(key="path-guide-reserve") {Spacer(Modifier.height(with(density){(viewportHeight*.56f).toDp()}))}
            item(key="path-mission") {
                Panel(color=Mist) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("TA MISSION DU JOUR");Spacer(Modifier.height(5.dp))
                            Text(next?.title ?: "À toi de choisir la suite",fontSize=23.sp,lineHeight=27.sp,fontWeight=FontWeight.ExtraBold)
                            Spacer(Modifier.height(5.dp));Text(p.todayXp.toString()+" / "+p.dailyGoal+" XP aujourd’hui",fontSize=13.sp,color=Muted)
                        }
                        Pico(Modifier.size(94.dp),mood=if(p.todayXp>=p.dailyGoal)MascotMood.CELEBRATE else if(p.streak>0)MascotMood.HAPPY else MascotMood.DETERMINED,
                            pose=if(p.todayXp>=p.dailyGoal)MascotPose.DANCE else MascotPose.WAVE)
                    }
                    LinearProgressIndicator(progress={(p.todayXp.toFloat()/p.dailyGoal).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(8.dp),color=Teal,trackColor=Color.White)
                    Button(feedbackClick {if(next!=null)model.startLesson(next) else model.route="practice"},
                        Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("path-start-next"),shape=RoundedCornerShape(15.dp)) {
                        Text(if(next!=null)"Commencer le cours" else "Choisir un défi",fontWeight=FontWeight.Bold,fontSize=16.sp)
                        Spacer(Modifier.width(10.dp));Icon(Icons.AutoMirrored.Rounded.ArrowForward,null,Modifier.size(19.dp))
                    }
                }
            }
            if(due.isNotEmpty())item(key="path-reviews") {
                Surface(onClick=feedbackClick {model.startQuestions("Les ondes reviennent",due.take(12))},shape=RoundedCornerShape(20.dp),color=Color(0xFFFFEDDC)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh,null,tint=Color(0xFFB76D36));Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {Text(due.size.toString()+" notions à revoir",fontWeight=FontWeight.Bold);Text("Une révision au bon moment, ça reste.",fontSize=12.sp,color=Muted)}
                        Icon(Icons.Rounded.ChevronRight,null)
                    }
                }
            }
            items(entries,key={it.key},contentType={it::class}) {entry->
                when(entry) {
                    is PathEntry.Section->PathSectionHeader(entry.section,entry.first)
                    is PathEntry.ChapterGroup->Column(Modifier.fillMaxWidth().testTag("path-chapter-group-"+entry.chapter.id),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        PathChapterHeader(entry.chapter,entry.index,entry.expanded,completed,started,ignored,
                            onIgnore={model.setLessonsIgnored(entry.chapter.lessons.map {it.id}.toSet(),it)},
                            enabled=!model.tour.active,modifier=Modifier.onSizeChanged {measuredRows[entry.key]=it.height}) {
                            val item=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key==entry.key}
                            val closing=expanded==entry.chapter.id
                            anchor=PathAnchor(entry.key,if(closing)null else .5f,item?.offset,item?.size ?: 0,System.nanoTime())
                            expanded=if(closing)null else entry.chapter.id
                            // Pin the touched group's top during the next measure. Its size can
                            // change, but closing the previous group cannot expose a stale index.
                            if(item!=null)listState.requestScrollToItem(prefixCount+entries.indexOf(entry),-item.offset)
                        }
                        if(entry.expanded)entry.chapter.lessons.forEachIndexed {lessonIndex,lesson->
                            PathLessonRow(model,lesson,entry.index,lessonIndex,lesson.id in completed,lesson.id in ignored,lesson.id==next?.id,!guideFirst||guideAnchored,
                                Modifier.onSizeChanged {measuredRows["lesson-"+lesson.id]=it.height})
                        }
                    }
                }
            }
            item(key="path-goodbye") {
                Box(Modifier.fillMaxWidth().padding(top=16.dp,bottom=8.dp).testTag("path-goodbye"),contentAlignment=Alignment.Center) {
                    PathFooterPico()
                }
            }
        }
        if(!(nextVisible&&nearTop))FloatingActionButton(onClick=feedbackClick {
            if(nextCentered||next==null)scope.launch {listState.animateScrollToItem(0)}
            else {
                expanded=nextChapter?.id
                anchor=PathAnchor("lesson-"+next.id,.5f,null,0,System.nanoTime())
            }
        },modifier=Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=20.dp+LocalNavigationContentOverlap.current).testTag("path-jump-next"),
            containerColor=Teal,contentColor=Color.White,shape=CircleShape) {
            Icon(Icons.Rounded.ArrowUpward,if(nextCentered||next==null)"Revenir en haut du parcours" else "Aller au prochain cours : "+next.title,Modifier.graphicsLayer {rotationZ=rotation})
        }
    }
}

@Composable private fun PathSectionHeader(section:CourseSection,first:Boolean) {
    Column(Modifier.fillMaxWidth().padding(top=if(first)10.dp else 30.dp,bottom=4.dp).testTag("path-section-"+section.id)) {
        Row(Modifier.fillMaxWidth().padding(bottom=20.dp).testTag("path-section-divider-"+section.id),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.width(22.dp).height(2.dp).background(Teal.copy(alpha=.18f),CircleShape))
            Icon(Icons.Rounded.Sensors,null,Modifier.padding(horizontal=9.dp).size(22.dp),tint=Teal.copy(alpha=.65f))
            Box(Modifier.width(22.dp).height(2.dp).background(Teal.copy(alpha=.18f),CircleShape))
        }
        Eyebrow("SECTION "+section.number);Spacer(Modifier.height(5.dp))
        Text(section.title,Modifier.semantics {heading()},fontSize=26.sp,lineHeight=30.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
        if(section.subtitle.isNotBlank())Text(section.subtitle,Modifier.padding(top=5.dp),fontSize=14.sp,lineHeight=20.sp,color=Muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PathChapterHeader(chapter:Chapter,index:Int,expanded:Boolean,completed:Set<String>,started:Set<String>,ignored:Set<String>,
    onIgnore:(Boolean)->Unit,enabled:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    val color=ChapterColors[index%ChapterColors.size]
    val finished=chapter.lessons.count {it.id in completed}
    val skipped=chapter.lessons.count {it.id in ignored&&it.id !in completed}
    val complete=chapter.lessons.isNotEmpty()&&finished==chapter.lessons.size
    val inProgress=!complete&&chapter.lessons.any {it.id in started}
    val allIgnored=chapter.lessons.isNotEmpty()&&chapter.lessons.all {it.id in ignored}
    val copyColor=if(allIgnored)Muted else Color.White
    var menu by remember(chapter.id) {mutableStateOf(false)}
    var ignoreRequested by rememberSaveable(chapter.id) {mutableStateOf(false)}
    LaunchedEffect(enabled){if(!enabled){menu=false;ignoreRequested=false}}
    Box {
    Surface(modifier=modifier.testTag("chapter-"+chapter.id).semantics {stateDescription=if(expanded)"Déplié" else "Replié"}
        .combinedClickable(role=Role.Button,onClick=feedbackClick(onClick),onLongClickLabel="Options du chapitre",
            onLongClick=if(enabled)feedbackClick {menu=true} else null),shape=RoundedCornerShape(22.dp),color=if(allIgnored)Color(0xFFE1E3DE) else color) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("%02d".format(index+1),Modifier.testTag("chapter-number-"+chapter.id).background(Color.White.copy(alpha=.18f),RoundedCornerShape(14.dp))
                .padding(12.dp),fontSize=21.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).testTag("chapter-content-"+chapter.id)) {
                Column(Modifier.fillMaxWidth().padding(end=if(complete||inProgress)64.dp else 0.dp).testTag("chapter-copy-"+chapter.id)) {
                    Text(chapter.title,Modifier.fillMaxWidth(),color=copyColor,fontSize=18.sp,fontWeight=FontWeight.Bold,textDecoration=if(allIgnored)TextDecoration.LineThrough else TextDecoration.None);Spacer(Modifier.height(2.dp))
                    Text(finished.toString()+"/"+chapter.lessons.size+(if(skipped>0)" · "+skipped+" ignoré"+(if(skipped>1)"s" else "") else "")+" · "+chapter.subtitle,
                        Modifier.fillMaxWidth(),color=copyColor.copy(alpha=.85f),fontSize=12.sp,lineHeight=16.sp)
                }
                if(complete||inProgress)Box(Modifier.matchParentSize()) {
                    ChapterCompletionSticker(chapter.id,Modifier.align(Alignment.CenterEnd).wrapContentSize(Alignment.CenterEnd,unbounded=true)
                        .size(68.dp).offset(x=12.dp).testTag((if(complete)"chapter-finished-badge-" else "chapter-started-badge-")+chapter.id),outlineOnly=!complete)
                }
            }
            Icon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null,tint=Color.White)
        }
    }
    DropdownMenu(menu,{menu=false}) {
        if(chapter.lessons.any {it.id !in ignored})DropdownMenuItem(text={Text("Ignorer ce chapitre")},leadingIcon={Icon(Icons.Rounded.SkipNext,null)},onClick={menu=false;ignoreRequested=true})
        if(chapter.lessons.any {it.id in ignored})DropdownMenuItem(text={Text("Réintégrer ce chapitre")},leadingIcon={Icon(Icons.Rounded.Undo,null)},onClick={menu=false;onIgnore(false)})
    }
    }
    if(ignoreRequested)PathIgnoreConfirmation(plural=true,onDismiss={ignoreRequested=false},onConfirm={ignoreRequested=false;onIgnore(true)})
}

/** Skipping changes only the path choice; rewards require actual learning. */
@Composable private fun PathIgnoreConfirmation(plural:Boolean,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("path-ignore-confirmation"),
        icon={Icon(Icons.Rounded.SkipNext,null)},title={Text(if(plural)"Ignorer ce chapitre ?" else "Ignorer ce cours ?")},
        text={Text(if(plural)
            "Ces cours compteront comme faits pour avancer dans le parcours, sans gagner d’XP ni créer de révisions. Tu pourras les réintégrer pour les suivre et gagner les XP habituels. Tes réponses et les cours déjà validés restent conservés."
        else "Ce cours comptera comme fait pour avancer dans le parcours, sans gagner d’XP ni créer de révisions. Tu pourras le réintégrer pour le suivre et gagner les XP habituels. Tes réponses et les cours déjà validés restent conservés.",Modifier.verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(feedbackClick(onConfirm),Modifier.testTag("path-confirm-ignore")){Text(if(plural)"Ignorer le chapitre" else "Ignorer le cours")}},
        dismissButton={TextButton(feedbackClick(onDismiss),Modifier.testTag("path-cancel-ignore")){Text("Annuler")}})
}

@Composable private fun PathLessonRow(model:AppModel,lesson:Lesson,chapterIndex:Int,lessonIndex:Int,done:Boolean,ignored:Boolean,active:Boolean,guideReady:Boolean,modifier:Modifier=Modifier) {
    val color=ChapterColors[chapterIndex%ChapterColors.size]
    var menu by remember(lesson.id) {mutableStateOf(false)}
    var openIgnored by rememberSaveable(lesson.id) {mutableStateOf<Boolean?>(null)}
    var ignoreRequested by rememberSaveable(lesson.id) {mutableStateOf(false)}
    LaunchedEffect(model.tour.active) {if(model.tour.active){menu=false;openIgnored=null;ignoreRequested=false}}
    fun open(preview:Boolean=false) {
        if(ignored)openIgnored=preview else if(preview)model.previewLesson(lesson) else model.startLesson(lesson)
    }
    val tourModifier=if(lesson.id==HamigoWelcomeLesson&&guideReady)Modifier.hamigoTourTarget("intro") else Modifier
    Row(modifier.then(tourModifier).fillMaxWidth().padding(end=2.dp).testTag("path-lesson-"+lesson.id)
        .semantics {if(ignored)stateDescription="Cours ignoré"},verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).padding(start=if(lessonIndex%2==0)12.dp else 30.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(onClick=feedbackClick {open()},modifier=Modifier.size(54.dp),shape=CircleShape,
                color=if(ignored)Color(0xFFE1E3DE) else if(done)color else if(active)Gold else Color.White,shadowElevation=if(active&&!ignored)5.dp else 1.dp) {
                Box(contentAlignment=Alignment.Center) {Icon(if(ignored)Icons.Rounded.SkipNext else if(done)Icons.Rounded.Replay else if(active)Icons.Rounded.PlayArrow else Icons.Rounded.RadioButtonUnchecked,
                    lesson.title,tint=if(ignored)Muted else if(done)Color.White else color)}
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f).testTag("lesson-copy-"+lesson.id)
                .clickable(interactionSource=remember(lesson.id){MutableInteractionSource()},indication=null,role=Role.Button,onClick=feedbackClick {open()}).padding(vertical=13.dp)) {
                // These belong to the sentence: a wrapped title keeps its full width,
                // and the small status group follows its final word whenever it fits.
                val purposes=buildList {
                    if(lesson.certificateWeight>0)add("certificate" to "Utile au certificat")
                    else if(lesson.explorationWeight>0)add("exploration" to "Explorer la radio")
                    if(done&&!ignored)add("completed" to "Cours terminé")
                }
                val title=buildAnnotatedString {
                    append(lesson.title)
                    purposes.forEachIndexed {index,(key,_)->
                        append(if(index==0)" " else "\u00a0")
                        appendInlineContent(key)
                    }
                }
                val inlineIcons=mapOf(
                    "certificate" to InlineTextContent(Placeholder(13.sp,13.sp,PlaceholderVerticalAlign.TextCenter)) {
                        Icon(Icons.Rounded.School,null,Modifier.fillMaxSize().testTag("lesson-certificate-"+lesson.id),tint=if(ignored)Muted else Teal)
                    },
                    "exploration" to InlineTextContent(Placeholder(13.sp,13.sp,PlaceholderVerticalAlign.TextCenter)) {
                        Icon(Icons.Rounded.Explore,null,Modifier.fillMaxSize().testTag("lesson-exploration-"+lesson.id),tint=if(ignored)Muted else Purple)
                    },
                    "completed" to InlineTextContent(Placeholder(17.sp,17.sp,PlaceholderVerticalAlign.TextCenter)) {
                        Icon(Icons.Rounded.CheckCircle,null,Modifier.fillMaxSize().testTag("lesson-completed-"+lesson.id),tint=Teal)
                    }
                )
                Text(title,Modifier.fillMaxWidth().testTag("lesson-title-"+lesson.id).semantics {
                    contentDescription=(listOf(lesson.title)+purposes.map {it.second}).joinToString(". ")
                },inlineContent=inlineIcons,fontWeight=if(active)FontWeight.ExtraBold else FontWeight.Bold,fontSize=15.sp,lineHeight=20.sp,
                    color=if(ignored)Muted else Ink,textDecoration=if(ignored)TextDecoration.LineThrough else TextDecoration.None)
                Text(if(ignored)"Cours ignoré" else lesson.summary,Modifier.testTag("lesson-summary-"+lesson.id),maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp,lineHeight=16.sp,color=Muted)
            }
        }
        Box {
            IconButton(feedbackClick {menu=true},Modifier.size(40.dp).testTag("lesson-menu-"+lesson.id),enabled=!model.tour.active) {Icon(Icons.Rounded.MoreVert,"Options du cours "+lesson.title,tint=Muted)}
            DropdownMenu(menu,{menu=false}) {
                DropdownMenuItem(text={Text("Lire le cours")},leadingIcon={Icon(Icons.Rounded.MenuBook,null)},onClick={menu=false;open(true)})
                DropdownMenuItem(text={Text(if(ignored)"Réintégrer ce cours" else "Ignorer ce cours")},leadingIcon={Icon(if(ignored)Icons.Rounded.Undo else Icons.Rounded.SkipNext,null)},
                    onClick={menu=false;if(ignored)model.setLessonsIgnored(setOf(lesson.id),false) else ignoreRequested=true})
            }
        }
    }
    if(ignoreRequested)PathIgnoreConfirmation(plural=false,onDismiss={ignoreRequested=false},onConfirm={ignoreRequested=false;model.setLessonsIgnored(setOf(lesson.id),true)})
    if(openIgnored!=null)AlertDialog(onDismissRequest={openIgnored=null},icon={Icon(Icons.Rounded.SkipNext,null)},title={Text("Ouvrir ce cours ignoré ?")},
        text={Text("Ce cours est ignoré dans ton parcours. Réintègre-le pour le suivre et gagner les XP habituels. Un cours déjà validé conserve ses récompenses précédentes.",Modifier.verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(feedbackClick {val preview=openIgnored==true;openIgnored=null;model.setLessonsIgnored(setOf(lesson.id),false);if(preview)model.previewLesson(lesson) else model.startLesson(lesson)}){Text("Réintégrer et ouvrir")}},
        dismissButton={TextButton(feedbackClick {openIgnored=null}){Text("Annuler")}})
}

/** Its ground shadow stays still while the radio makes one complete turn. */
@Composable private fun PathFooterPico() {
    val animate=ValueAnimator.areAnimatorsEnabled()
    val rotation=remember {Animatable(0f)}
    var turns by remember {mutableIntStateOf(0)}
    val transition=rememberInfiniteTransition(label="path-footer-wave")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1700,easing=LinearEasing),RepeatMode.Restart),label="path-footer-wave-phase")
    LaunchedEffect(turns) {if(turns>0){if(animate)rotation.animateTo(turns*360f,tween(850)) else rotation.snapTo(turns*360f)}}
    Canvas(Modifier.size(132.dp).testTag("path-footer-pico").clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,
        onClickLabel="Faire tourner Pico",onClick=feedbackAction(FeedbackCue.PICO_FOOTER){turns++}).semantics {
            contentDescription="Pico, la mascotte radio. Pico te salue avec ses lunettes de soleil"
            stateDescription=if(rotation.isRunning)"Pico fait un tour" else "Pico te salue"
        }) {
        val native=drawContext.canvas.nativeCanvas
        val bounds=android.graphics.RectF(0f,0f,size.width,size.height)
        PicoRenderer.drawShadow(native,bounds)
        val saved=native.save()
        native.rotate(rotation.value,size.width/2f,size.height/2f)
        PicoRenderer.draw(native,bounds,MascotMood.COOL,MascotPose.WAVE,if(animate)phase else .25f,drawGroundShadow=false)
        native.restoreToCount(saved)
    }
}
