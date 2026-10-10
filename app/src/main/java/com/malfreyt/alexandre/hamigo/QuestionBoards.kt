package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.random.Random

private val wireColors=listOf(Teal,Purple,Color(0xFF547FCD),Color(0xFFAA6743),Color(0xFFAC5377),Color(0xFF548345))
private val correctWireColors=listOf(Color(0xFF23845D),Color(0xFF3A906D),Color(0xFF477D55),Color(0xFF248977))
private val wrongWireColors=listOf(Color(0xFFB65049),Color(0xFFC56557),Color(0xFFAD4C61),Color(0xFFBA6046))

/** Tapping or dragging works from either end. Dots and wires share one foreground canvas. */
@Composable fun MatchBoard(q:Question,key:String,matches:Map<Int,Int>,enabled:Boolean,feedback:Boolean?=null,
    onChange:(Map<Int,Int>)->Unit) {
    val shuffled=remember(key) {q.pairs.indices.shuffled(Random(q.id.hashCode()))}
    val rects=remember(key) {mutableStateMapOf<Pair<Boolean,Int>,Rect>()}
    var board by remember(key) {mutableStateOf(Rect.Zero)}
    var selected by remember(key) {mutableStateOf<Pair<Boolean,Int>?>(null)}
    var dragging by remember(key) {mutableStateOf<Pair<Boolean,Int>?>(null)}
    var finger by remember(key) {mutableStateOf(Offset.Zero)}
    val currentMatches by rememberUpdatedState(matches)
    val currentChange by rememberUpdatedState(onChange)
    val tactile=LocalAppFeedback.current
    fun join(a:Pair<Boolean,Int>,b:Pair<Boolean,Int>) {
        if(a.first==b.first)return
        val l=if(a.first)a.second else b.second;val r=if(a.first)b.second else a.second
        currentChange(currentMatches.filterKeys {it!=l}.filterValues {it!=r}+(l to r));selected=null
        tactile?.event(FeedbackCue.SNAP)
    }
    fun tap(side:Pair<Boolean,Int>) {
        val old=selected
        if(old!=null && old.first!=side.first)join(old,side)
        else {selected=if(old==side)null else side;tactile?.event(FeedbackCue.SELECT)}
    }
    fun accent(source:Int):Color=if(feedback==null)wireColors[source%wireColors.size]
        else if(matches[source]==source)correctWireColors[source%correctWireColors.size]
        else wrongWireColors[source%wrongWireColors.size]
    fun anchor(side:Pair<Boolean,Int>):Offset?=rects[side]?.let {Offset(if(side.first)it.right else it.left,it.center.y)-board.topLeft}
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned {board=it.boundsInRoot()}.testTag("match-board")) {
        val narrow=maxWidth<340.dp
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(if(narrow)28.dp else 52.dp),verticalAlignment=Alignment.Top) {
            listOf(true,false).forEach {leftSide ->
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    (if(leftSide)q.pairs.indices.toList() else shuffled).forEach {index ->
                        val side=leftSide to index
                        val source=if(leftSide)index.takeIf {it in matches} else matches.entries.firstOrNull {it.value==index}?.key
                        val active=selected==side || dragging==side || source!=null
                        val color=source?.let(::accent) ?: Purple
                        Surface(onClick=feedbackClick {tap(side)},enabled=enabled,
                            modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("match-${if(leftSide)"left" else "right"}-$index")
                                .onGloballyPositioned {rects[side]=it.boundsInRoot()}
                                .pointerInput(key,index,leftSide,enabled) {
                                    if(enabled)detectDragGestures(
                                        onDragStart={point->tactile?.event(FeedbackCue.DRAG);dragging=side;selected=null;finger=(rects[side]?.topLeft ?: Offset.Zero)+point},
                                        onDrag={change,_->change.consume();finger=(rects[side]?.topLeft ?: Offset.Zero)+change.position},
                                        onDragEnd={
                                            val target=rects.entries.firstOrNull {it.key.first!=leftSide && it.value.contains(finger)}?.key
                                            if(target!=null)join(side,target)
                                            dragging=null
                                        },onDragCancel={dragging=null})
                                },shape=RoundedCornerShape(15.dp),
                            color=if(active)color.copy(alpha=if(feedback==null).10f else .14f) else Color.White,
                            border=BorderStroke(if(active)2.dp else 1.dp,if(active)color else Color(0xFFD5DEDA))) {
                            Box(Modifier.padding(horizontal=8.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                                MorseAwareText(if(leftSide)q.pairs[index].left else q.pairs[index].right,
                                    Modifier.fillMaxWidth().padding(horizontal=if(narrow&&!active)4.dp else if(narrow)16.dp else 20.dp),fontSize=if(narrow)12.sp else 14.sp,lineHeight=if(narrow)17.sp else 19.sp,
                                    fontWeight=if(active)FontWeight.Bold else FontWeight.Medium,textAlign=TextAlign.Center)
                                if(active)Box(Modifier.align(if(leftSide)Alignment.CenterEnd else Alignment.CenterStart)
                                    .size(22.dp).background(color,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center) {
                                    Text("${(source ?: index)+1}",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize().zIndex(2f).testTag("match-wires")) {
            fun wire(a:Offset,b:Offset,color:Color) {
                val bend=(b.x-a.x)*.48f
                drawPath(Path().apply {moveTo(a.x,a.y);cubicTo(a.x+bend,a.y,b.x-bend,b.y,b.x,b.y)},
                    color,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
            }
            matches.forEach {(l,r)->val a=anchor(true to l);val b=anchor(false to r);if(a!=null&&b!=null)wire(a,b,accent(l).copy(alpha=.88f))}
            selected?.let {side->anchor(side)?.let {a->drawLine(Purple,a,a+Offset(if(side.first)16.dp.toPx() else -16.dp.toPx(),0f),3.dp.toPx(),StrokeCap.Round)}}
            dragging?.let {side->anchor(side)?.let {a->wire(a,finger-board.topLeft,Purple)}}
            rects.keys.forEach {side->
                val source=if(side.first)side.second.takeIf {it in matches} else matches.entries.firstOrNull {it.value==side.second}?.key
                val color=source?.let(::accent) ?: if(side==selected||side==dragging)Purple else Muted
                anchor(side)?.let {point->drawCircle(Cream,5.5.dp.toPx(),point);drawCircle(color,3.5.dp.toPx(),point)}
            }
        }
    }
}

/** Stable item keys keep the grabbed card alive when its numbered slot changes. */
@Composable fun OrderBoard(q:Question,order:List<Int>,enabled:Boolean,feedback:Boolean?,onOrder:(List<Int>)->Unit) {
    val bounds=remember(q.id) {mutableStateMapOf<Int,Rect>()}
    val handles=remember(q.id) {mutableStateMapOf<Int,Rect>()}
    var dragged by remember(q.id) {mutableStateOf<Int?>(null)}
    var pointerY by remember(q.id) {mutableFloatStateOf(0f)}
    var grabOffset by remember(q.id) {mutableFloatStateOf(0f)}
    var original by remember(q.id) {mutableStateOf(order)}
    val currentOrder by rememberUpdatedState(order)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentChange by rememberUpdatedState(onOrder)
    val haptic=LocalAppFeedback.current
    fun begin(item:Int,y:Float) {original=currentOrder;dragged=item;pointerY=y;grabOffset=y-(bounds[item]?.center?.y ?: y);haptic?.event(FeedbackCue.DRAG)}
    fun move(item:Int,y:Float) {
        pointerY=y
        val target=bounds.filterKeys {it in currentOrder}.minByOrNull {abs(it.value.center.y-(y-grabOffset))}?.key ?: return
        if(target!=item) {
            val next=currentOrder.toMutableList();val to=next.indexOf(target);next.remove(item);next.add(to,item)
            currentChange(next);haptic?.event(FeedbackCue.ORDER_STEP)
        }
    }
    fun finish(){if(currentOrder!=original)haptic?.event(FeedbackCue.SNAP);dragged=null}
    fun cancel(){if(currentEnabled)currentChange(original);dragged=null}
    Column(Modifier.fillMaxWidth().testTag("order-board"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        order.forEachIndexed {position,item->key(item) {
            val color=if(feedback==null)Purple else if(item==position)Teal else Color(0xFFB65049)
            Box(Modifier.fillMaxWidth().zIndex(if(dragged==item)2f else 0f).onGloballyPositioned {bounds[item]=it.boundsInRoot()}
                .testTag("order-item-$item").semantics {
                    stateDescription="Position ${position+1} sur ${order.size}"
                    customActions=if(enabled)listOf(
                        CustomAccessibilityAction("Monter") {val i=currentOrder.indexOf(item);if(i>0){currentChange(currentOrder.toMutableList().apply {removeAt(i);add(i-1,item)});true}else false},
                        CustomAccessibilityAction("Descendre") {val i=currentOrder.indexOf(item);if(i<currentOrder.lastIndex){currentChange(currentOrder.toMutableList().apply {removeAt(i);add(i+1,item)});true}else false}
                    )else emptyList()
                }.pointerInput(q.id,item,enabled) {
                    if(enabled)detectDragGesturesAfterLongPress(onDragStart={begin(item,(bounds[item]?.top ?: 0f)+it.y)},
                        onDrag={change,_->change.consume();move(item,(bounds[item]?.top ?: 0f)+change.position.y)},onDragEnd=::finish,onDragCancel=::cancel)
                }) {
                Surface(Modifier.fillMaxWidth().graphicsLayer {
                    translationY=if(dragged==item)pointerY-grabOffset-(bounds[item]?.center?.y ?: pointerY) else 0f
                    scaleX=if(dragged==item)1.015f else 1f;scaleY=if(dragged==item)1.015f else 1f
                // An opaque pastel masks the shadow inside the card during correction.
                // A translucent surface lets its own platform shadow show through as a grey slab.
                },shape=RoundedCornerShape(16.dp),color=if(feedback==null)Color.White else color.copy(alpha=.12f).compositeOver(Cream),
                    shadowElevation=if(dragged==item)8.dp else 1.dp,border=BorderStroke(1.dp,if(dragged==item)Purple else color.copy(alpha=.28f))) {
                    Row(Modifier.heightIn(min=64.dp).padding(horizontal=10.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(30.dp).background(color.copy(alpha=.13f),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                            Text("${position+1}",fontSize=15.sp,fontWeight=FontWeight.ExtraBold,color=color)
                        }
                        MorseAwareText(q.choices[item],Modifier.weight(1f),fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold)
                        Canvas(Modifier.size(30.dp).testTag("order-handle-$item").onGloballyPositioned {handles[item]=it.boundsInRoot()}
                            .pointerInput(q.id,item,enabled) {
                                if(enabled)detectDragGestures(onDragStart={begin(item,(handles[item]?.top ?: 0f)+it.y)},
                                    onDrag={change,_->change.consume();move(item,(handles[item]?.top ?: 0f)+change.position.y)},onDragEnd=::finish,onDragCancel=::cancel)
                            }) {
                            repeat(3){row->repeat(2){col->drawCircle(if(enabled)Muted else Muted.copy(alpha=.35f),1.8.dp.toPx(),Offset(size.width*(.38f+col*.24f),size.height*(.28f+row*.22f)))}}
                        }
                    }
                }
            }
        } }
    }
}
