package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/** The drawing follows measured slots, so enlarged text never disconnects the wires. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CircuitBoard(q:Question,placements:Map<Int,String>,enabled:Boolean,feedback:Boolean?,onChange:(Map<Int,String>)->Unit) {
    val slots=remember(q.id){mutableStateMapOf<Int,Rect>()}
    var board by remember(q.id){mutableStateOf(Rect.Zero)}
    var tray by remember(q.id){mutableStateOf(Rect.Zero)}
    var selected by remember(q.id){mutableStateOf<String?>(null)}
    var dragging by remember(q.id){mutableStateOf<String?>(null)}
    var finger by remember(q.id){mutableStateOf(Offset.Zero)}
    val items=remember(q.id,q.presentationSeed){q.choices.shuffled(kotlin.random.Random(q.presentationSeed))}
    val currentPlacement by rememberUpdatedState(placements)
    val currentChange by rememberUpdatedState(onChange)
    val haptics=LocalAppFeedback.current
    val available=items.filterNot {it in placements.values}
    fun place(index:Int,value:String){
        currentChange(currentPlacement.filterValues {it!=value}+(index to value))
        selected=null
        haptics?.event(FeedbackCue.SNAP)
    }
    LaunchedEffect(placements) {if(selected in placements.values)selected=null}
    val receiver=q.visual=="circuit:receiver"
    val measurement=q.visual=="circuit:measurement"
    Column(Modifier.fillMaxWidth().testTag("circuit-board"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned {board=it.boundsInRoot()}) {
            Canvas(Modifier.matchParentSize()) {
                val centers=q.pairs.indices.mapNotNull {index->slots[index]?.let {it.center-board.topLeft}}
                if(centers.isNotEmpty()) {
                    val wire=Teal.copy(alpha=.72f);val stroke=2.5.dp.toPx()
                    if(receiver) {
                        centers.zipWithNext().forEach {(a,b)->
                            drawLine(wire,a,b,stroke)
                            val y=(a.y+b.y)/2
                            drawLine(wire,Offset(a.x-5.dp.toPx(),y-5.dp.toPx()),Offset(a.x,y),stroke)
                            drawLine(wire,Offset(a.x+5.dp.toPx(),y-5.dp.toPx()),Offset(a.x,y),stroke)
                        }
                    } else if(measurement&&centers.size==2) {
                        // Ammeter in the main loop. Voltmeter across the fixed resistance.
                        val a=centers[0];val v=centers[1]
                        val top=12.dp.toPx();val bottom=size.height-12.dp.toPx()
                        val xR=(a.x+v.x)/2
                        drawPath(Path().apply {moveTo(a.x,a.y);lineTo(a.x,top);lineTo(xR,top);lineTo(xR,bottom);lineTo(a.x,bottom);close()},wire,style=Stroke(stroke))
                        drawPath(Path().apply {moveTo(xR,top);lineTo(v.x,top);lineTo(v.x,bottom);lineTo(xR,bottom)},wire,style=Stroke(stroke))
                        val middle=(top+bottom)/2
                        drawRect(Cream,Offset(xR-9.dp.toPx(),middle-23.dp.toPx()),androidx.compose.ui.geometry.Size(18.dp.toPx(),46.dp.toPx()))
                        drawRect(wire,Offset(xR-7.dp.toPx(),middle-22.dp.toPx()),androidx.compose.ui.geometry.Size(14.dp.toPx(),44.dp.toPx()),style=Stroke(stroke))
                        listOf(top,bottom).forEach {y->drawCircle(wire,4.dp.toPx(),Offset(xR,y))}
                        // A source is connected in the lower branch; it is not a short circuit.
                        val xS=(a.x+xR)/2
                        drawLine(Cream,Offset(xS-10.dp.toPx(),bottom),Offset(xS+10.dp.toPx(),bottom),6.dp.toPx())
                        drawLine(wire,Offset(xS-4.dp.toPx(),bottom-10.dp.toPx()),Offset(xS-4.dp.toPx(),bottom+10.dp.toPx()),stroke)
                        drawLine(wire,Offset(xS+4.dp.toPx(),bottom-6.dp.toPx()),Offset(xS+4.dp.toPx(),bottom+6.dp.toPx()),stroke)
                    } else {
                        val x=size.width*.10f
                        val first=centers.first();val last=centers.last()
                        drawPath(Path().apply {moveTo(first.x,first.y);lineTo(x,first.y);lineTo(x,last.y);lineTo(last.x,last.y)},wire,style=Stroke(stroke))
                        centers.zipWithNext().forEach {(a,b)->drawLine(wire,a,b,stroke,StrokeCap.Round)}
                    }
                }
            }
            @Composable fun Slot(index:Int,modifier:Modifier=Modifier) {
                val pair=q.pairs[index]
                val value=placements[index]
                var menu by remember(q.id,index){mutableStateOf(false)}
                val hovered=enabled&&dragging!=null&&slots[index]?.contains(finger)==true
                val correct=feedback!=null&&WrittenAnswers.correct(value.orEmpty(),listOf(pair.right))
                val accent=if(feedback==null)Teal else if(correct)Teal else Coral
                Box(modifier.onGloballyPositioned {slots[index]=it.boundsInRoot()}) {
                    Surface(onClick={if(selected!=null)place(index,selected!!) else menu=true},enabled=enabled,
                        shape=RoundedCornerShape(14.dp),color=if(hovered)Gold.copy(alpha=.35f).compositeOver(Cream) else if(value==null)Cream else accent.copy(alpha=.1f).compositeOver(Cream),
                        border=BorderStroke(if(hovered)2.5.dp else 1.5.dp,if(hovered)Teal else accent.copy(alpha=.65f)),modifier=Modifier.fillMaxWidth().testTag("circuit-slot-$index").semantics {if(hovered)stateDescription="Prêt à recevoir le composant"}) {
                        Column(Modifier.padding(12.dp).heightIn(min=52.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(pair.left,fontSize=12.sp,color=Muted)
                            Text(value ?: "Choisir…",fontSize=15.sp,fontWeight=FontWeight.Bold,color=accent)
                        }
                    }
                    DropdownMenu(menu&&enabled,{menu=false}) {
                        if(value!=null)DropdownMenuItem(text={Text("Retirer "+value)},leadingIcon={Icon(Icons.Rounded.Close,null)},onClick={
                            currentChange(currentPlacement-index);selected=null;menu=false
                        })
                        available.forEach {component->DropdownMenuItem(text={Text(component)},onClick={place(index,component);menu=false})}
                    }
                }
            }
            if(measurement&&q.pairs.size==2)Row(Modifier.fillMaxWidth().padding(vertical=34.dp),horizontalArrangement=Arrangement.spacedBy(48.dp),verticalAlignment=Alignment.CenterVertically) {
                Slot(0,Modifier.weight(1f));Slot(1,Modifier.weight(1f))
            } else Column(Modifier.fillMaxWidth().padding(start=if(receiver)24.dp else 54.dp,end=16.dp,top=8.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(26.dp)) {
                q.pairs.indices.forEach {Slot(it)}
            }
        }
        Text("Glisse un composant vers sa place, ou touche une place pour choisir.",fontSize=12.sp,color=Muted)
        Box(Modifier.fillMaxWidth().testTag("circuit-tray").onGloballyPositioned {tray=it.boundsInRoot()}) {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                available.forEach {component ->
                    var origin by remember(component){mutableStateOf(Offset.Zero)}
                    Surface(onClick={selected=if(selected==component)null else component},enabled=enabled,
                        shape=RoundedCornerShape(12.dp),color=if(selected==component)Gold else Color.White,
                        border=BorderStroke(1.dp,if(selected==component)Teal else Teal.copy(alpha=.25f)),
                        modifier=Modifier.heightIn(min=48.dp).testTag("circuit-component-$component")
                            .semantics {this.selected=selected==component}
                            .onGloballyPositioned {origin=it.boundsInRoot().topLeft}
                            .pointerInput(q.id,component,enabled) {
                                if(enabled)detectDragGestures(onDragStart={point->dragging=component;selected=null;finger=origin+point;haptics?.event(FeedbackCue.DRAG)},
                                    onDrag={change,_->change.consume();finger=origin+change.position},
                                    onDragEnd={slots.entries.firstOrNull {it.value.contains(finger)}?.let {place(it.key,component)};dragging=null},onDragCancel={dragging=null})
                            }) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Rounded.DragIndicator,null,Modifier.size(16.dp),tint=Muted)
                            Text(component,fontSize=14.sp,color=if(enabled)Ink else Muted)
                        }
                    }
                }
            }
            dragging?.let {value->Surface(color=Gold,shape=RoundedCornerShape(12.dp),shadowElevation=5.dp,
                modifier=Modifier.zIndex(3f).graphicsLayer {translationX=finger.x-tray.left;translationY=finger.y-tray.top-40.dp.toPx()}) {
                Text(value,Modifier.padding(10.dp),fontWeight=FontWeight.Bold,color=Ink)
            }}
        }
    }
}
