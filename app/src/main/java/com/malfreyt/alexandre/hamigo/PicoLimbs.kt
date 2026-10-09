package com.malfreyt.alexandre.hamigo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Limb layers share the body's coordinate system; each pose selects its own geometry. */
internal object PicoLimbs {
    private class Pen(val canvas:Canvas,val color:Int) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=this@Pen.color;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        fun line(x:Float,y:Float,x2:Float,y2:Float,width:Float=6f){paint.strokeWidth=width;canvas.drawLine(x,y,x2,y2,paint)}
        fun path(points:List<Pair<Float,Float>>,width:Float=6f) {
            paint.strokeWidth=width
            val path=Path();points.forEachIndexed {i,p->if(i==0)path.moveTo(p.first,p.second) else path.lineTo(p.first,p.second)}
            canvas.drawPath(path,paint)
        }
    }
    fun legs(canvas:Canvas,pose:MascotPose,ink:Int) {
        val p=Pen(canvas,ink)
        when(pose) {
            MascotPose.JUMP -> {p.path(listOf(38f to 97f,29f to 109f,19f to 105f));p.path(listOf(89f to 97f,99f to 104f,107f to 100f))}
            MascotPose.DANCE -> {p.path(listOf(37f to 96f,32f to 112f,22f to 113f));p.path(listOf(90f to 96f,94f to 111f,105f to 111f))}
            else -> {p.line(38f,96f,33f,113f);p.line(88f,96f,95f,113f);p.line(29f,115f,38f,115f);p.line(91f,115f,101f,115f)}
        }
    }
    fun arms(canvas:Canvas,pose:MascotPose,phase:Float,pointLeft:Boolean,coral:Int,gold:Int) {
        val p=Pen(canvas,coral)
        when(pose) {
            MascotPose.HUG -> {p.path(listOf(22f to 66f,10f to 77f,34f to 82f),7f);p.path(listOf(105f to 66f,117f to 77f,96f to 83f),7f)}
            MascotPose.POINT -> if(pointLeft) {p.line(105f,67f,116f,78f,7f);p.line(23f,64f,8f,54f,7f);p.line(8f,54f,3f,54f,5f)} else {p.line(23f,67f,12f,78f,7f);p.line(105f,64f,120f,54f,7f);p.line(120f,54f,125f,54f,5f)}
            MascotPose.WAVE -> {
                p.line(23f,67f,10f,78f,7f);p.path(listOf(105f to 67f,117f to 55f,116f to (41f+phase*5f)),7f)
                Pen(canvas,gold).apply {line(112f,37f,106f,31f,2f);line(123f,37f,126f,30f,2f)}
            }
            MascotPose.JUMP,MascotPose.DANCE -> {p.path(listOf(23f to 66f,12f to 53f,12f to 43f),7f);p.path(listOf(104f to 65f,117f to 52f,117f to 43f),7f)}
            else -> {p.line(23f,68f,14f,84f,7f);p.line(105f,68f,115f,84f,7f)}
        }
    }
}
