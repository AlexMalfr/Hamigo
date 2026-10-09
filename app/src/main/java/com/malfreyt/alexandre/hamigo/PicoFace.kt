package com.malfreyt.alexandre.hamigo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Transparent facial features. Each frame draws one set of eyes and one mouth, never a mask. */
internal object PicoFace {
    fun draw(canvas:Canvas,mood:MascotMood,mouthOpen:Float?,eyesClosed:Boolean,pointLeft:Boolean,
        ink:Int=Color.rgb(23,63,66),coral:Int=Color.rgb(255,120,105)) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color:Int){p.color=color;p.style=Paint.Style.FILL}
        fun stroke(color:Int=ink,width:Float=3f){fill(color);p.style=Paint.Style.STROKE;p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND}
        fun line(x:Float,y:Float,x2:Float,y2:Float,width:Float=3f,color:Int=ink){stroke(color,width);canvas.drawLine(x,y,x2,y2,p)}
        fun dot(x:Float,y:Float,r:Float,color:Int=ink){fill(color);canvas.drawCircle(x,y,r,p)}
        fun oval(l:Float,t:Float,r:Float,b:Float,color:Int=ink){fill(color);canvas.drawOval(l,t,r,b,p)}
        fun arc(l:Float,t:Float,r:Float,b:Float,start:Float,sweep:Float){stroke();canvas.drawArc(l,t,r,b,start,sweep,false,p)}
        val lx=43f-if(pointLeft)2f else 0f;val rx=62f-if(pointLeft)2f else 0f
        // Eyes (including eyebrows) are selected before any eye is painted.
        if(eyesClosed) {
            arc(38f,55f,48f,63f,10f,160f);arc(57f,55f,67f,63f,10f,160f)
        } else when(mood) {
            MascotMood.CELEBRATE -> {arc(38f,55f,48f,65f,190f,160f);arc(57f,55f,67f,65f,190f,160f)}
            MascotMood.GOOFY -> {dot(lx,60f,3f);arc(57f,56f,67f,65f,10f,160f)}
            MascotMood.THINKING -> {dot(44f,59f,3f);dot(63f,58f,3f);line(39f,52f,46f,50f,2.5f);line(59f,50f,66f,53f,2.5f)}
            MascotMood.SAD -> {arc(38f,56f,48f,63f,10f,160f);arc(57f,56f,67f,63f,10f,160f);line(39f,50f,45f,53f,2f);line(60f,53f,66f,50f,2f)}
            MascotMood.DETERMINED -> {dot(lx,60f,3f);dot(rx,60f,3f);line(38f,50f,47f,54f);line(59f,54f,68f,50f)}
            MascotMood.SLEEPY -> {line(39f,60f,47f,60f,2.5f);line(58f,60f,66f,60f,2.5f)}
            MascotMood.SHOCKED -> {dot(lx,60f,4.3f);dot(rx,60f,4.3f);dot(42f,58f,1.5f,Color.WHITE);dot(61f,58f,1.5f,Color.WHITE);line(39f,50f,46f,49f,2f);line(59f,49f,66f,50f,2f)}
            MascotMood.HAPPY -> {dot(lx,60f,3f);dot(rx,60f,3f)}
        }
        // Mouth: speech replaces the resting mouth and tongue rather than covering them.
        if(mouthOpen!=null) {
            val opening=mouthOpen.coerceIn(0f,1f)
            if(opening<.15f)arc(46f,69f,61f,77f,5f,165f)
            else {oval(46f,71f-opening*3f,61f,74f+opening*7f);if(opening>.55f)oval(50f,76f,59f,79f,coral)}
        } else when(mood) {
            MascotMood.CELEBRATE -> {oval(44f,66f,62f,80f);oval(49f,74f,59f,79f,coral)}
            MascotMood.GOOFY -> {arc(44f,65f,62f,76f,0f,180f);oval(54f,70f,62f,82f,coral);line(57f,75f,57f,80f,1.3f,Color.rgb(230,80,85))}
            MascotMood.THINKING -> line(48f,73f,58f,72f)
            MascotMood.SAD -> arc(45f,72f,60f,82f,195f,150f)
            MascotMood.DETERMINED -> arc(45f,65f,60f,75f,10f,145f)
            MascotMood.SLEEPY -> oval(49f,69f,57f,78f)
            MascotMood.SHOCKED -> oval(49f,69f,58f,81f)
            MascotMood.HAPPY -> arc(44f,65f,62f,79f,0f,180f)
        }
        // Cheeks remain independent of eye and mouth states.
        dot(35f,72f,if(eyesClosed)4f else 3f,coral);dot(70f,72f,if(eyesClosed)4f else 3f,coral)
    }
}
