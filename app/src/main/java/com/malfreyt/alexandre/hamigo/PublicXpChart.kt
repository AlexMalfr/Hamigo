package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One scale for both people; missing history is never plotted as activity. */
@Composable internal fun PublicXpChart(
    values:List<Int?>, labels:List<String>, goal:Int?,
    emptyLabel:String="Pas de progression sur cette période.",
    todayIndex:Int?=null, comparison:List<Int?>?=null
) {
    val orange=Color(0xFFD97827)
    val ownBlue=Color(0xFF94C8CD)
    val ownOrange=Color(0xFFF0B985)
    val maximum=maxOf(1,values.filterNotNull().maxOrNull() ?: 0,comparison?.filterNotNull()?.maxOrNull() ?: 0,goal ?: 0)*1.12f
    val hasActivity=values.any {it!=null&&it>0}
    val emptyText=if(values.any {it!=null})emptyLabel else "Historique non disponible"
    val hourly=values.size>12
    val accessible=values.mapIndexed {i,x->
        "${labels.getOrElse(i){i.toString()}}${if(i==todayIndex)", aujourd’hui" else ""} : ${x?.let {"$it XP"} ?: "sans données"}"+
            (comparison?.getOrNull(i)?.let {", toi : $it XP"} ?: "")
    }.joinToString(" ; ")
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clearAndSetSemantics {contentDescription=(goal?.let {"Objectif journalier : $it XP. "} ?: "")+accessible}) {
            if(todayIndex!=null&&todayIndex in values.indices)Canvas(Modifier.matchParentSize()) {
                val step=size.width/values.size
                drawRoundRect(orange.copy(alpha=.09f),Offset(step*todayIndex,0f),Size(step,size.height),CornerRadius(6.dp.toPx()))
            }
            Column {
                if(!hourly)Row(Modifier.fillMaxWidth().padding(top=5.dp)) { values.forEachIndexed {i,x->
                    Text(x?.toString() ?: "—",Modifier.weight(1f),fontSize=10.sp,textAlign=TextAlign.Center,
                        color=if(i==todayIndex)orange else Muted,fontWeight=if(i==todayIndex)FontWeight.Bold else FontWeight.Normal)
                }}
                Box(contentAlignment=Alignment.Center) {
                    Canvas(Modifier.fillMaxWidth().height(148.dp).testTag(if(goal!=null)"weekly-goal-line" else "hourly-xp-chart")) {
                        val step=size.width/values.size.coerceAtLeast(1)
                        val baseline=size.height-6.dp.toPx();val plotHeight=baseline-8.dp.toPx()
                        val width=step*.54f
                        drawLine(Mist,Offset(0f,baseline),Offset(size.width,baseline),1.dp.toPx())
                        values.forEachIndexed {i,x->
                            val h=if(x==null)2.dp.toPx() else if(x==0)0f else maxOf(3.dp.toPx(),plotHeight*x/maximum)
                            if(h>0) {
                                val achieved=goal!=null&&x!=null&&x>=goal
                                val color=if(x==null)Muted.copy(alpha=.35f) else if(i==todayIndex)Coral else Teal
                                val colors=if(achieved)listOf(Color(0xFFEBC45E),Color(0xFFFFE4A0),Color(0xFFF2C354)) else listOf(color,color)
                                drawRoundRect(Brush.horizontalGradient(colors),Offset(step*(i+.5f)-width/2,baseline-h),Size(width,h),CornerRadius(3.dp.toPx()))
                            }
                            comparison?.getOrNull(i)?.takeIf {it>0}?.let {xp->
                                val ch=maxOf(3.dp.toPx(),plotHeight*xp/maximum)
                                val stroke=1.6.dp.toPx()
                                drawRoundRect(if(i==todayIndex)ownOrange else ownBlue,Offset(step*(i+.5f)-width/2+stroke/2,baseline-ch+stroke/2),
                                    Size((width-stroke).coerceAtLeast(1f),(ch-stroke).coerceAtLeast(1f)),CornerRadius((3.dp.toPx()-stroke/2).coerceAtLeast(0f)),
                                    style=Stroke(stroke,pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx()))))
                            }
                        }
                        goal?.let { val y=baseline-plotHeight*it/maximum
                            drawLine(orange,Offset(0f,y),Offset(size.width,y),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx())))
                        }
                    }
                    if(!hasActivity&&comparison?.any {it!=null&&it>0}!=true)Text(emptyText,color=Muted,fontSize=12.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(horizontal=10.dp))
                }
                if(hourly)Row(Modifier.fillMaxWidth()) {
                    labels.filter {it.isNotBlank()}.forEach {Text(it,Modifier.weight(1f),fontSize=10.sp,color=Muted)}
                } else Row(Modifier.fillMaxWidth().padding(bottom=5.dp)) {labels.forEachIndexed {i,label->
                    Text(label,Modifier.weight(1f),fontSize=11.sp,textAlign=TextAlign.Center,
                        color=if(i==todayIndex)orange else Muted,fontWeight=if(i==todayIndex)FontWeight.Bold else FontWeight.Normal)
                }}
            }
        }
        if(comparison?.any {it!=null}==true)Row(Modifier.padding(top=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(21.dp,9.dp)) {drawLine(ownBlue,Offset(0f,size.height/2),Offset(size.width,size.height/2),1.6.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx())))}
            Text("Toi",color=Muted,fontSize=11.sp)
        }
    }
}
