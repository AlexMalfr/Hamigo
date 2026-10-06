package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A small foil seal, with stable placement so recomposition never makes it jump. */
@Composable internal fun ChapterCompletionSticker(chapterId:String,modifier:Modifier=Modifier) {
    val variation=remember(chapterId) { chapterId.hashCode().toUInt().toLong() }
    val angle=((variation%17L)-8L).toFloat()
    val shift=((variation/17L%5L)-2L).toInt()
    Canvas(modifier.size(62.dp).offset(y=shift.dp).semantics {contentDescription="Chapitre terminé"}) {
        val radius=size.minDimension*.43f
        val center=Offset(size.width/2f,size.height/2f)
        rotate(angle,center) {
            // Rounded serrations preserve the cut-paper silhouette at small sizes.
            val points=List(48) {index ->
                val theta=index*2.0*PI/48-PI/2
                val r=radius*if(index%2==0)1f else .91f
                center+Offset((cos(theta)*r).toFloat(),(sin(theta)*r).toFloat())
            }
            val seal=Path().apply {
                val first=(points.last()+points.first())*.5f
                moveTo(first.x,first.y)
                points.indices.forEach {index ->
                    val point=points[index]
                    val next=(point+points[(index+1)%points.size])*.5f
                    quadraticBezierTo(point.x,point.y,next.x,next.y)
                }
                close()
            }
            // Soft cast shadow is restricted to the sticker's own reserved space.
            for(layer in 5 downTo 1) translate(0f,layer*.45.dp.toPx()) {
                drawPath(seal,Color(0xFF35240B).copy(alpha=.065f))
            }
            drawPath(seal,Brush.linearGradient(
                listOf(Color(0xFFFFF0AE),Color(0xFFE8B33B),Color(0xFFFFDF76),Color(0xFFC58B1F)),
                center-Offset(radius,radius),center+Offset(radius,radius)))
            drawPath(seal,Color(0xFFFFF4C2).copy(alpha=.85f),style=Stroke(.8.dp.toPx()))
            drawCircle(Color(0xFFB37916).copy(alpha=.62f),radius*.80f,center,style=Stroke(.8.dp.toPx()))
            drawCircle(Color(0xFFFFF4BC).copy(alpha=.8f),radius*.75f,center,style=Stroke(.65.dp.toPx()))
            // Two restrained glints give the foil depth without adding visual noise.
            val inset=radius*.71f
            drawArc(Color.White.copy(alpha=.6f),205f,76f,false,
                center-Offset(inset,inset),Size(inset*2,inset*2),style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round))
            drawArc(Color(0xFF9B6711).copy(alpha=.26f),28f,73f,false,
                center-Offset(inset,inset),Size(inset*2,inset*2),style=Stroke(1.2.dp.toPx(),cap=StrokeCap.Round))
            val check=Path().apply {
                moveTo(center.x-radius*.36f,center.y)
                lineTo(center.x-radius*.08f,center.y+radius*.25f)
                lineTo(center.x+radius*.40f,center.y-radius*.29f)
            }
            val checkStroke=Stroke(3.7.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)
            translate(0f,1.dp.toPx()) {drawPath(check,Color(0xFF956311).copy(alpha=.65f),style=checkStroke)}
            drawPath(check,Color(0xFFFFFEF1),style=checkStroke)
        }
    }
}
