package com.malfreyt.alexandre.hamigo

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

internal data class ConfettiPiece(val side:Int,val start:Float,val speedX:Float,val speedY:Float,val spin:Float,val tilt:Float,val size:Float,val colour:Int)
internal fun celebrationPieces(seed:Long,passed:Boolean):List<ConfettiPiece> {
    val random=Random(seed)
    return List(if(passed)56 else 24) {i->
        ConfettiPiece(i%2,random.nextFloat()*.14f,.22f+random.nextFloat()*.34f,1.08f+random.nextFloat()*.16f,
            (random.nextFloat()-.5f)*360f,random.nextFloat()*180f,7f+random.nextFloat()*6f,random.nextInt(4))
    }
}

/** Two brief fans from the bottom. No input layer and no endless particle engine. */
@Composable internal fun SessionCelebration(session:Session,passed:Boolean,modifier:Modifier=Modifier) {
    val feedback=LocalAppFeedback.current
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val motion=remember {Animatable(0f)}
    val pieces=remember(session){celebrationPieces(session.started,passed)}
    var visible by remember {mutableStateOf(false)}
    val animationAllowed=remember {Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}
    LaunchedEffect(session,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if(!session.resultPresented) {
                session.resultPresented=true
                delay(130)
                feedback?.event(if(passed)FeedbackCue.COMPLETE else FeedbackCue.FINISH)
                if(animationAllowed) {
                    visible=true
                    motion.animateTo(1f,tween(4200,easing=LinearEasing))
                    visible=false
                }
            } else visible=false
        }
    }
    if(visible)Canvas(modifier.fillMaxSize().testTag("session-confetti")) {
        // A longer flight played slowly: higher/wider arcs, without a faster initial blast.
        val clock=motion.value*2.8f
        val colours=listOf(Teal,Coral,Gold,Color(0xFF78BFC0))
        pieces.forEach {piece->
            val t=clock-piece.start
            if(t>=0) {
                val x=size.width*(if(piece.side==0).12f+piece.speedX*t else .88f-piece.speedX*t)
                val y=size.height*(1.015f-piece.speedY*t+.40f*t*t)
                val opacity=((2.8f-clock)/.65f).coerceIn(0f,1f)*(if(passed).92f else .7f)
                val w=piece.size*density;val h=w*(.38f+.62f*abs(cos(t*5+piece.tilt)))
                rotate(piece.tilt+piece.spin*t,Offset(x,y)) {
                    if(piece.colour==3)drawCircle(colours[piece.colour].copy(alpha=opacity),w*.36f,Offset(x,y))
                    else drawRoundRect(colours[piece.colour].copy(alpha=opacity),Offset(x-w/2,y-h/2),Size(w,h),CornerRadius(w*.12f))
                }
            }
        }
    }
}
