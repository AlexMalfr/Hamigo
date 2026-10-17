package com.malfreyt.alexandre.hamigo.platform

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.*

internal data class WidgetSymbol(val bounds:RectF,val vector:ImageVector,val color:Int)

/** Reserve space before drawing, so icons share native text's sizing and never cover it. */
internal object WidgetSymbols {
    fun placement(bounds:RectF,text:String,font:Float,bold:Boolean,centered:Boolean,fontScale:Float):Pair<RectF,RectF>? {
        if(bounds.width()<34f||bounds.height()<10f)return null
        val side=minOf(20f,bounds.height()*.6f,bounds.width()*.2f)
        val gap=(side*.28f).coerceAtLeast(2f)
        val available=bounds.width()-side-gap
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=font*fontScale;typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
        val fm=paint.fontMetrics
        paint.textSize*=minOf(1f,(available-1f).coerceAtLeast(1f)/paint.measureText(text).coerceAtLeast(1f),(bounds.height()-1f)/(fm.descent-fm.ascent).coerceAtLeast(1f))
        val width=paint.measureText(text).coerceAtMost(available)
        val left=if(centered)bounds.left+(bounds.width()-side-gap-width)/2 else bounds.left
        return RectF(left+side+gap,bounds.top,if(centered)left+side+gap+width else bounds.right,bounds.bottom) to
            RectF(left,bounds.centerY()-side/2,left+side,bounds.centerY()+side/2)
    }
    fun draw(canvas:Canvas,symbol:WidgetSymbol) {
        val saved=canvas.save();val b=symbol.bounds;val vector=symbol.vector
        canvas.translate(b.left,b.top);canvas.scale(b.width()/vector.viewportWidth,b.height()/vector.viewportHeight)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=symbol.color}
        fun group(group:VectorGroup) {
            val save=canvas.save()
            canvas.translate(group.translationX+group.pivotX,group.translationY+group.pivotY)
            canvas.rotate(group.rotation);canvas.scale(group.scaleX,group.scaleY);canvas.translate(-group.pivotX,-group.pivotY)
            group.forEach {node->when(node) {
                is VectorGroup->group(node)
                is VectorPath->{paint.alpha=(android.graphics.Color.alpha(symbol.color)*node.fillAlpha).toInt();canvas.drawPath(PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(),paint)}
            }}
            canvas.restoreToCount(save)
        }
        group(vector.root);canvas.restoreToCount(saved)
    }
}
