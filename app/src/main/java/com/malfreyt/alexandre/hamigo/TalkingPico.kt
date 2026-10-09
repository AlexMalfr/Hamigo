package com.malfreyt.alexandre.hamigo

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sin

internal val PicoBodyCenter=HorizontalAlignmentLine {a,b->minOf(a,b)}

/** The same speech motion in questions and course introductions. Idle drawings retain their face. */
@Composable internal fun TalkingPico(modifier:Modifier,talking:Boolean,mood:MascotMood=MascotMood.HAPPY,
    pose:MascotPose=MascotPose.POINT,mirrored:Boolean=false,pointLeft:Boolean=false,eyesClosed:Boolean=false,
    idleMotion:Boolean=false,reaction:Int=0) {
    val (pulse,mouthPhase)=if(talking) {
        val transition=rememberInfiniteTransition(label="Pico talks")
        val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(2200,easing=LinearEasing),RepeatMode.Restart),label="syllable")
        val mouth by transition.animateFloat(0f,1f,infiniteRepeatable(tween(720,easing=LinearEasing),RepeatMode.Restart),label="mouth")
        phase to mouth
    }else 0f to 0f
    val idlePhase=if(idleMotion) {
        val transition=rememberInfiniteTransition(label="Pico breathes")
        val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(4800,easing=LinearEasing),RepeatMode.Restart),label="breath")
        phase
    }else 0f
    val sway=sin(idlePhase*2*Math.PI).toFloat()
    val lift=with(LocalDensity.current){.8.dp.toPx()}
    val bounce=remember {Animatable(0f)}
    LaunchedEffect(reaction) {
        if(reaction>0){bounce.snapTo(0f);bounce.animateTo(.045f,tween(110));bounce.animateTo(0f,tween(320))}
    }
    Canvas(modifier.layout {measurable,constraints->
        val drawing=measurable.measure(constraints)
        val inset=minOf(drawing.width,drawing.height)*.055f
        val center=PicoRenderer.bodyCenterY(android.graphics.RectF(inset,inset,drawing.width-inset,drawing.height-inset),pose,.25f).toInt()
        layout(drawing.width,drawing.height,mapOf(PicoBodyCenter to center)) {drawing.placeRelative(0,0)}
    }) {
        val canvas=drawContext.canvas.nativeCanvas
        val inset=minOf(size.width,size.height)*.055f
        val bounds=android.graphics.RectF(inset,inset,size.width-inset,size.height-inset)
        // Ground stays put; only the figure breathes, sways and speaks. Insets protect the antenna.
        PicoRenderer.drawShadow(canvas,bounds)
        val body=if(talking)sin(pulse*2*Math.PI).toFloat() else 0f
        val mouth=if(talking)(sin(mouthPhase*2*Math.PI).toFloat()+1f)/2f else null
        val save=canvas.save()
        canvas.translate(size.width/2f,size.height/2f-sway*lift)
        canvas.rotate(sway*.9f)
        canvas.scale((if(mirrored)-1f else 1f)*(1f+body*.014f+sway*.006f+bounce.value),1f-body*.014f-sway*.005f+bounce.value)
        canvas.translate(-size.width/2f,-size.height/2f)
        PicoRenderer.draw(canvas,bounds,mood,pose,if(idleMotion&&!talking)idlePhase else .25f,
            mouthOpen=mouth,eyesClosed=eyesClosed||(!talking&&idleMotion&&idlePhase>.97f),pointLeft=pointLeft,drawGroundShadow=false)
        canvas.restoreToCount(save)
    }
}
