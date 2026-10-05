package com.malfreyt.alexandre.hamigo

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Both the activity and the calculator's dialog report anchors in device coordinates.
internal fun LayoutCoordinates.screenBounds(view: View): Rect {
    val screen = IntArray(2); val window = IntArray(2)
    view.getLocationOnScreen(screen); view.getLocationInWindow(window)
    return boundsInWindow().translate(androidx.compose.ui.geometry.Offset(
        (screen[0]-window[0]).toFloat(), (screen[1]-window[1]).toFloat()))
}

internal class BackMotionAnchors { var settings by mutableStateOf<Rect?>(null) }
internal val LocalBackMotionAnchors = staticCompositionLocalOf<BackMotionAnchors?> { null }
internal val LocalAnimatedBack = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable internal fun stickyHeaderDetached(state: LazyListState): Boolean {
    val detached by remember(state) { derivedStateOf {
        state.firstVisibleItemIndex>0 || state.firstVisibleItemScrollOffset>state.layoutInfo.beforeContentPadding
    } }
    return detached
}

@Composable internal fun Modifier.stickyHeaderShadow(state: LazyListState): Modifier {
    val detached=stickyHeaderDetached(state)
    val strength by animateFloatAsState(if(detached)1f else 0f,tween(150),label="sticky-header-shadow")
    return drawBehind {
        // Lists keep 16 dp side gutters for their content; the pinned surface and its
        // shadow extend across those gutters, all the way to the viewport edges.
        val gutter = 16.dp.toPx()
        if(strength>0f) {
            for (spread in HamigoEdgeShadowLayers downTo 1) {
                drawRect(Color.Black.copy(alpha=HamigoEdgeShadowAlpha*strength),
                    topLeft=androidx.compose.ui.geometry.Offset(-gutter,size.height),
                    size=androidx.compose.ui.geometry.Size(size.width+2*gutter,spread.dp.toPx()))
            }
        }
        drawRect(Cream,topLeft=androidx.compose.ui.geometry.Offset(-gutter,0f),
            size=androidx.compose.ui.geometry.Size(size.width+2*gutter,size.height))
    }
}

@Composable internal fun FlippingFlashcard(question: Question, answerSide: Boolean, onFlip: () -> Unit) {
    val rotation by animateFloatAsState(if(answerSide) 180f else 0f, tween(360, easing=FastOutSlowInEasing), label="flashcard-flip")
    val backVisible = rotation > 90f
    val density = LocalDensity.current
    Surface(
        onClick=onFlip,
        modifier=Modifier.fillMaxWidth().heightIn(min=260.dp).testTag("flashcard")
            .semantics { stateDescription=if(backVisible) "Réponse" else "Question" }
            .graphicsLayer { rotationY=rotation; cameraDistance=14f*density.density; clip=false },
        shape=RoundedCornerShape(24.dp), color=if(backVisible) Mist else Color.White,
        shadowElevation=9.dp,
    ) {
        Column(Modifier.graphicsLayer { rotationY=if(backVisible)180f else 0f }
            .padding(22.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(16.dp,Alignment.CenterVertically)) {
            Text(if(backVisible) "RÉPONSE" else "QUESTION",fontSize=12.sp,color=Teal,fontWeight=FontWeight.Bold)
            val prompt=if(question.topic=="morse")MorseReference.characterName(question.prompt) else question.prompt
            MorseAwareText(if(backVisible)question.choices.firstOrNull().orEmpty() else prompt,
                fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight.ExtraBold)
            if(backVisible && question.explanation.isNotBlank())MorseAwareText(question.explanation,fontSize=14.sp,lineHeight=20.sp,color=Muted)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.SwapHoriz,null,Modifier.size(18.dp),tint=Teal)
                Text(if(backVisible) "Toucher pour revoir la question" else "Toucher pour voir la réponse",fontSize=12.sp,color=Muted)
            }
        }
    }
}
