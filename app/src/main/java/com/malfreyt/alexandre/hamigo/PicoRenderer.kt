package com.malfreyt.alexandre.hamigo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private val ink = Color.rgb(23, 63, 66)
    private val teal = Color.rgb(8, 127, 130)
    private val coral = Color.rgb(255, 120, 105)
    private val gold = Color.rgb(255, 198, 111)
    private val face = Color.rgb(255, 236, 204)

    fun draw(canvas: Canvas, bounds: RectF, mood: MascotMood = MascotMood.HAPPY,
             pose: MascotPose = MascotPose.WAVE, phase: Float = 0f) {
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
        fun path(points: List<Pair<Float,Float>>,color: Int,width: Float=5f) {stroke(color,width);val p=Path();points.forEachIndexed{i,xy->if(i==0)p.moveTo(xy.first,xy.second)else p.lineTo(xy.first,xy.second)};canvas.drawPath(p,paint)}
        fill(Color.argb(18,23,63,66));canvas.drawOval(20f,112f,108f,122f,paint)
        val figure = canvas.save()
        val bob = if(pose==MascotPose.JUMP) -5f-5f*sin(phase*Math.PI).toFloat() else sin(phase*2*Math.PI).toFloat()*1.4f
        canvas.translate(0f,bob)
        canvas.rotate(if(pose==MascotPose.DANCE) sin(phase*2*Math.PI).toFloat()*8f else if(mood==MascotMood.GOOFY) -6f else 0f,64f,78f)
        when(pose) {
            MascotPose.JUMP -> {path(listOf(38f to 97f,29f to 109f,19f to 105f),ink,6f);path(listOf(89f to 97f,99f to 104f,107f to 100f),ink,6f)}
            MascotPose.DANCE -> {path(listOf(37f to 96f,32f to 112f,22f to 113f),ink,6f);path(listOf(90f to 96f,94f to 111f,105f to 111f),ink,6f)}
            else -> {line(38f,96f,33f,113f,ink,6f);line(88f,96f,95f,113f,ink,6f);line(29f,115f,38f,115f,ink,6f);line(91f,115f,101f,115f,ink,6f)}
        }
        when(pose) {
            MascotPose.HUG -> {path(listOf(22f to 66f,10f to 77f,34f to 82f),coral,7f);path(listOf(105f to 66f,117f to 77f,96f to 83f),coral,7f)}
            MascotPose.POINT -> {line(23f,67f,12f,78f,coral,7f);line(105f,64f,120f,54f,coral,7f);line(120f,54f,125f,54f,coral,5f)}
            MascotPose.WAVE -> {line(23f,67f,10f,78f,coral,7f);path(listOf(105f to 67f,117f to 55f,116f to (41f+phase*5f)),coral,7f);line(112f,37f,106f,31f,gold,2f);line(123f,37f,126f,30f,gold,2f)}
            MascotPose.JUMP, MascotPose.DANCE -> {path(listOf(23f to 66f,12f to 53f,12f to 43f),coral,7f);path(listOf(104f to 65f,117f to 52f,117f to 43f),coral,7f)}
            else -> {line(23f,68f,14f,84f,coral,7f);line(105f,68f,115f,84f,coral,7f)}
        }
        line(64f,22f,64f,42f,gold,6f);circle(64f,20f,5f,coral)
        arc(42f,0f,86f,35f,215f,110f,teal,3f);arc(51f,9f,77f,28f,215f,110f,teal,2.5f)
        fill(teal);canvas.drawRoundRect(23f,43f,105f,102f,17f,17f,paint)
        fill(face);canvas.drawRoundRect(23f,38f,105f,97f,17f,17f,paint)
        fill(Color.argb(145,255,255,255));canvas.drawRoundRect(30f,44f,75f,85f,11f,11f,paint)
        circle(87f,59f,9f,coral);circle(84f,56f,3f,Color.argb(100,255,255,255))
        repeat(3){line(80f,77f+it*5,94f,77f+it*5,Color.argb(130,23,63,66),2f)}
        val lx=43f;val rx=62f;val ey=60f
        when(mood) {
            MascotMood.CELEBRATE -> {arc(38f,55f,48f,65f,190f,160f);arc(57f,55f,67f,65f,190f,160f);oval(44f,66f,62f,80f,ink);oval(49f,74f,59f,79f,coral)}
            MascotMood.GOOFY -> {circle(lx,ey,3f,ink);arc(57f,56f,67f,65f,10f,160f);arc(44f,65f,62f,76f,0f,180f);oval(54f,70f,62f,82f,coral);line(57f,75f,57f,80f,Color.rgb(230,80,85),1.3f)}
            MascotMood.THINKING -> {circle(44f,59f,3f,ink);circle(63f,58f,3f,ink);line(39f,52f,46f,50f,ink,2.5f);line(59f,50f,66f,53f,ink,2.5f);line(48f,73f,58f,72f,ink,3f)}
            MascotMood.SAD -> {arc(38f,56f,48f,63f,10f,160f);arc(57f,56f,67f,63f,10f,160f);arc(45f,72f,60f,82f,195f,150f);line(39f,50f,45f,53f,ink,2f);line(60f,53f,66f,50f,ink,2f)}
            MascotMood.DETERMINED -> {circle(lx,ey,3f,ink);circle(rx,ey,3f,ink);line(38f,50f,47f,54f,ink,3f);line(59f,54f,68f,50f,ink,3f);arc(45f,65f,60f,75f,10f,145f)}
            MascotMood.SLEEPY -> {line(39f,60f,47f,60f,ink,2.5f);line(58f,60f,66f,60f,ink,2.5f);oval(49f,69f,57f,78f,ink);fill(teal);paint.textSize=12f;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD;canvas.drawText("z",107f,27f,paint);paint.textSize=9f;canvas.drawText("z",115f,18f,paint)}
            MascotMood.SHOCKED -> {circle(lx,ey,4.3f,ink);circle(rx,ey,4.3f,ink);circle(42f,58f,1.5f,Color.WHITE);circle(61f,58f,1.5f,Color.WHITE);oval(49f,69f,58f,81f,ink);line(39f,50f,46f,49f,ink,2f);line(59f,49f,66f,50f,ink,2f)}
            MascotMood.HAPPY -> {circle(lx,ey,3f,ink);circle(rx,ey,3f,ink);arc(44f,65f,62f,79f,0f,180f)}
        }
        circle(35f,72f,3f,Color.argb(125,255,120,105));circle(70f,72f,3f,Color.argb(125,255,120,105))
        canvas.restoreToCount(figure)
        if(mood==MascotMood.CELEBRATE) {line(9f,24f,13f,19f,gold,3f);line(112f,10f,117f,13f,coral,3f);circle(21f,17f,2f,teal);circle(113f,95f,2.5f,gold);line(5f,97f,9f,101f,coral,3f)}
        if(mood==MascotMood.THINKING) {circle(104f,25f,2.5f,teal);circle(112f,16f,4f,teal);circle(119f,7f,5.5f,teal)}
        canvas.restoreToCount(save)
    }
}
