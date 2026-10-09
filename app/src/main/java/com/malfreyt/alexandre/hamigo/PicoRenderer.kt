package com.malfreyt.alexandre.hamigo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.sin

enum class MascotMood(val description: String) {
    HAPPY("Pico sourit"), THINKING("Pico réfléchit"), CELEBRATE("Pico fête ta réussite"),
    SAD("Pico t'encourage après une erreur"), GOOFY("Pico fait le clown"),
    DETERMINED("Pico se concentre"), SLEEPY("Pico a sommeil"), SHOCKED("Pico est surpris")
}
enum class MascotPose { IDLE, WAVE, JUMP, DANCE, POINT, HUG }

/** One transparent vector drawing shared by Compose, notification artwork and share cards. */
object PicoRenderer {
    /** Centre of the rectangular enclosure (38..102), including the drawing's body bob. */
    fun bodyCenterY(bounds:RectF,pose:MascotPose,phase:Float):Float {
        val scale=min(bounds.width(),bounds.height())/128f
        val bob=if(pose==MascotPose.JUMP)-5f-5f*sin(phase*Math.PI).toFloat() else sin(phase*2*Math.PI).toFloat()*1.4f
        return bounds.centerY()+(6f+bob)*scale
    }
    private val ink = Color.rgb(23, 63, 66)
    private val teal = Color.rgb(8, 127, 130)
    private val coral = Color.rgb(255, 120, 105)
    private val gold = Color.rgb(255, 198, 111)
    private val face = Color.rgb(255, 236, 204)

    fun draw(canvas: Canvas, bounds: RectF, mood: MascotMood = MascotMood.HAPPY,
             pose: MascotPose = MascotPose.WAVE, phase: Float = 0f,
             mouthOpen: Float? = null, eyesClosed: Boolean = false,pointLeft:Boolean=false,drawGroundShadow:Boolean=true) {
        val scale = min(bounds.width(), bounds.height()) / 128f
        if (scale <= 0f) return
        val save = canvas.save()
        canvas.translate(bounds.centerX() - 64 * scale, bounds.centerY() - 64 * scale)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color: Int) { paint.color=color; paint.style=Paint.Style.FILL; paint.alpha=255 }
        fun stroke(color: Int, width: Float) { fill(color);paint.style=Paint.Style.STROKE;paint.strokeWidth=width;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND }
        fun line(x1: Float,y1: Float,x2: Float,y2: Float,color: Int=ink,width: Float=5f) { stroke(color,width);canvas.drawLine(x1,y1,x2,y2,paint) }
        fun circle(x: Float,y: Float,r: Float,color: Int) { fill(color);canvas.drawCircle(x,y,r,paint) }
        fun oval(left: Float,top: Float,right: Float,bottom: Float,color: Int) {fill(color);canvas.drawOval(left,top,right,bottom,paint)}
        fun arc(left: Float,top: Float,right: Float,bottom: Float,start: Float,sweep: Float,color: Int=ink,width: Float=3f) {stroke(color,width);canvas.drawArc(left,top,right,bottom,start,sweep,false,paint)}
        // fill() resets the paint opacity; set the ground shadow's alpha afterwards.
        if(drawGroundShadow){fill(ink);paint.alpha=45;canvas.drawOval(20f,112f,108f,122f,paint)}
        val figure = canvas.save()
        val bob = if(pose==MascotPose.JUMP) -5f-5f*sin(phase*Math.PI).toFloat() else sin(phase*2*Math.PI).toFloat()*1.4f
        canvas.translate(0f,bob)
        canvas.rotate(if(pose==MascotPose.DANCE) sin(phase*2*Math.PI).toFloat()*8f else if(mood==MascotMood.GOOFY) -6f else 0f,64f,78f)
        PicoLimbs.legs(canvas,pose,ink)
        PicoLimbs.arms(canvas,pose,phase,pointLeft,coral,gold)
        line(64f,22f,64f,42f,gold,6f);circle(64f,20f,5f,coral)
        arc(42f,0f,86f,35f,215f,110f,teal,3f);arc(51f,9f,77f,28f,215f,110f,teal,2.5f)
        fill(teal);canvas.drawRoundRect(23f,43f,105f,102f,17f,17f,paint)
        fill(face);canvas.drawRoundRect(23f,38f,105f,97f,17f,17f,paint)
        fill(Color.argb(145,255,255,255));canvas.drawRoundRect(30f,44f,75f,85f,11f,11f,paint)
        circle(87f,59f,9f,coral);circle(84f,56f,3f,Color.argb(100,255,255,255))
        repeat(3){line(80f,77f+it*5,94f,77f+it*5,Color.argb(130,23,63,66),2f)}
        PicoFace.draw(canvas,mood,mouthOpen,eyesClosed,pointLeft,ink,coral)
        if(mood==MascotMood.SLEEPY) {
            fill(teal);paint.textSize=12f;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD;canvas.drawText("z",107f,27f,paint);paint.textSize=9f;canvas.drawText("z",115f,18f,paint)
        }
        canvas.restoreToCount(figure)
        if(mood==MascotMood.CELEBRATE) {line(9f,24f,13f,19f,gold,3f);line(112f,10f,117f,13f,coral,3f);circle(21f,17f,2f,teal);circle(113f,95f,2.5f,gold);line(5f,97f,9f,101f,coral,3f)}
        if(mood==MascotMood.THINKING) {circle(104f,25f,2.5f,teal);circle(112f,16f,4f,teal);circle(119f,7f,5.5f,teal)}
        canvas.restoreToCount(save)
    }

    fun drawShadow(canvas:Canvas,bounds:RectF) {
        val scale=min(bounds.width(),bounds.height())/128f
        val save=canvas.save()
        canvas.translate(bounds.centerX()-64*scale,bounds.centerY()-64*scale);canvas.scale(scale,scale)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=ink;alpha=45}
        canvas.drawOval(20f,112f,108f,122f,paint);canvas.restoreToCount(save)
    }
}
