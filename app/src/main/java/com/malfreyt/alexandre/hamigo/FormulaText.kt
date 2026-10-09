package com.malfreyt.alexandre.hamigo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max

/** Native typesetting of the recognised TeX-equivalent tree: no WebView, network or answer rewrite. */
@Composable internal fun FormulaAwareAnswer(text:String,modifier:Modifier=Modifier,bold:Boolean=false,color:Color=Ink) {
    val formula=remember(text){MathFormula.parse(text)}
    if(formula==null){MorseAwareText(text,modifier,fontSize=15.sp,lineHeight=21.sp,fontWeight=if(bold)FontWeight.Bold else FontWeight.Medium,color=color);return}
    val density=LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pixels=with(density){23.sp.toPx()}
        val box=remember(formula,pixels,color,bold){typeset(formula,pixels,color.toArgb(),bold)}
        val scale=(constraints.maxWidth/box.width).coerceAtMost(1f)
        Canvas(Modifier.fillMaxWidth().height(with(density){((box.up+box.down+pixels*.12f)*scale).toDp()})
            .testTag("math-formula").clearAndSetSemantics {this.text=AnnotatedString(text)}) {
            val canvas=drawContext.canvas.nativeCanvas;val saved=canvas.save()
            canvas.scale(scale,scale);box.draw(canvas,0f,box.up+pixels*.06f);canvas.restoreToCount(saved)
        }
    }
}

private data class MathBox(val width:Float,val up:Float,val down:Float,val draw:(Canvas,Float,Float)->Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun FormulaAwareQuestion(text:String,modifier:Modifier,fontSize:TextUnit,towardsLeft:Boolean) {
    val fragments=remember(text){MathFormula.fragments(text)}
    if(fragments.none {it.formula!=null}) {
        MorseAwareText(text,modifier,fontSize=fontSize,lineHeight=(fontSize.value*1.32f).sp,fontWeight=FontWeight.ExtraBold,textAlign=if(towardsLeft)TextAlign.Start else TextAlign.End)
        return
    }
    val density=LocalDensity.current
    BoxWithConstraints(modifier) {
        val pixels=with(density){fontSize.toPx()}
        val availableWidth=constraints.maxWidth
        FlowRow(Modifier.fillMaxWidth().clearAndSetSemantics {this.text=AnnotatedString(text)},
            horizontalArrangement=Arrangement.spacedBy(4.dp,if(towardsLeft)Alignment.Start else Alignment.End),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            fragments.forEach {fragment->
                if(fragment.formula==null)Regex("\\S+").findAll(fragment.source).forEach {token->
                    Text(token.value,Modifier.align(Alignment.CenterVertically),fontSize=fontSize,lineHeight=(fontSize.value*1.32f).sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                }
                else {
                    val box=typeset(fragment.formula,pixels,Ink.toArgb(),true)
                    val scale=(availableWidth/box.width).coerceAtMost(1f)
                    val width=with(density){(box.width*scale).toDp()}
                    val height=with(density){((box.up+box.down+pixels*.12f)*scale).toDp()}
                    Canvas(Modifier.width(width).height(height).align(Alignment.CenterVertically)) {
                        val c=drawContext.canvas.nativeCanvas;val saved=c.save();c.scale(scale,scale)
                        box.draw(c,0f,box.up+pixels*.06f);c.restoreToCount(saved)
                    }
                }
            }
        }
    }
}

private fun typeset(node:Formula,size:Float,color:Int,bold:Boolean):MathBox {
    fun child(value:Formula,factor:Float=1f)=typeset(value,size*factor,color,bold)
    fun line(canvas:Canvas,x1:Float,y1:Float,x2:Float,y2:Float) {
        canvas.drawLine(x1,y1,x2,y2,Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;strokeWidth=max(1f,size*.055f)})
    }
    return when(node) {
        is Formula.Atom -> {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color=color;textSize=size
                typeface=Typeface.create("serif",when {node.italic&&bold->Typeface.BOLD_ITALIC;node.italic->Typeface.ITALIC;bold->Typeface.BOLD;else->Typeface.NORMAL})
            }
            val metrics=paint.fontMetrics
            MathBox(paint.measureText(node.value),-metrics.ascent,metrics.descent){canvas,x,y->canvas.drawText(node.value,x,y,paint)}
        }
        is Formula.Join -> {
            val left=child(node.left);val right=child(node.right);val sign=child(Formula.Atom(node.symbol,false))
            val gap=if(node.symbol.isEmpty())size*.045f else size*.20f
            MathBox(left.width+sign.width+right.width+2*gap,max(left.up,max(sign.up,right.up)),max(left.down,max(sign.down,right.down))) {c,x,y->
                left.draw(c,x,y);sign.draw(c,x+left.width+gap,y);right.draw(c,x+left.width+sign.width+2*gap,y)
            }
        }
        is Formula.Fraction -> {
            val n=child(node.numerator,.9f);val d=child(node.denominator,.9f);val width=max(n.width,d.width)+size*.32f
            val rule=-size*.22f;val ny=rule-size*.14f-n.down;val dy=rule+size*.14f+d.up
            MathBox(width,n.up-ny,d.down+dy){c,x,y->n.draw(c,x+(width-n.width)/2,y+ny);d.draw(c,x+(width-d.width)/2,y+dy);line(c,x,y+rule,x+width,y+rule)}
        }
        is Formula.Root -> {
            val inner=child(node.value);val up=inner.up+size*.16f;val lead=size*.53f;val width=lead+inner.width+size*.12f
            MathBox(width,up,max(inner.down,size*.08f)){c,x,y->
                val path=Path().apply{moveTo(x,y-size*.18f);lineTo(x+size*.12f,y-size*.28f);lineTo(x+size*.25f,y+size*.05f);lineTo(x+size*.43f,y-up);lineTo(x+width,y-up)}
                c.drawPath(path,Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;style=Paint.Style.STROKE;strokeWidth=max(1f,size*.055f);strokeJoin=Paint.Join.ROUND})
                inner.draw(c,x+lead,y)
            }
        }
        is Formula.Power -> {
            val base=child(node.value);val power=child(node.exponent,.64f);val rise=-base.up*.72f
            MathBox(base.width+power.width, max(base.up,power.up-rise),base.down){c,x,y->base.draw(c,x,y);power.draw(c,x+base.width,y+rise)}
        }
        is Formula.Index -> {
            val base=child(node.value);val index=child(node.index,.62f);val drop=size*.23f
            MathBox(base.width+index.width,base.up,max(base.down,index.down+drop)){c,x,y->base.draw(c,x,y);index.draw(c,x+base.width,y+drop)}
        }
        is Formula.Group -> {
            val value=child(node.value);val left=child(Formula.Atom("(",false));val right=child(Formula.Atom(")",false))
            MathBox(left.width+value.width+right.width,max(value.up,left.up),max(value.down,left.down)){c,x,y->left.draw(c,x,y);value.draw(c,x+left.width,y);right.draw(c,x+left.width+value.width,y)}
        }
    }
}
